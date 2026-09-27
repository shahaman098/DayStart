package com.loudwake.app.engine

import android.content.Context
import android.util.Log
import com.loudwake.app.data.AlarmItem
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.SessionPhase
import com.loudwake.app.data.WakeSession
import java.util.Calendar

/**
 * DayStart wake rules:
 * Ring → Gate → Relapse Guard → done
 * Stop / abandon without gate → Chase
 * Relapse miss → restart gate + chase
 *
 * Safe Silence is a limited mute until the next ring (not a skip).
 */
object WakeEngine {
    const val RING_INTERVAL_MS = 60_000L
    const val CHASE_COUNT = 5
    /** Max full chase rounds before the session auto-ends (stops FGS / pressure). */
    const val MAX_CHASE_CYCLES = 3
    const val RELAPSE_WAIT_MS = 300_000L
    const val RELAPSE_WINDOW_MS = 300_000L
    const val GATE_IDLE_MS = 45_000L
    const val MAX_SAFE_SILENCE = 2
    private const val TAG = "WakeEngine"

    fun isWithinWakeWindow(now: Calendar = Calendar.getInstance()): Boolean = true

    fun hasActiveSession(context: Context): Boolean {
        val p = DayStartStore.session(context).phase
        return p != SessionPhase.IDLE && p != SessionPhase.DONE
    }

    fun startFromAlarm(context: Context, alarm: AlarmItem) {
        val session = WakeSession(
            alarmId = alarm.id,
            phase = SessionPhase.RINGING,
            chaseRemaining = 0,
            tone = alarm.tone,
            systemSoundUri = alarm.systemSoundUri,
            voiceEnabled = alarm.voiceEnabled,
            voiceStyle = alarm.voiceStyle,
            mode = alarm.mode,
            scheduleTasks = alarm.scheduleTasks,
            wakePhrase = alarm.wakePhrase,
            challengeIndex = 0,
            safeSilence = false,
            safeSilenceUsed = 0,
            nextRingAt = 0L
        )
        DayStartStore.saveSession(context, session)
        DayStartService.startRinging(context)
    }

    fun startTest(context: Context, alarm: AlarmItem) {
        if (hasActiveSession(context)) return
        DayStartScheduler.cancelAllDynamic(context)
        startFromAlarm(context, alarm)
    }

    /** Re-ring after Safe Silence, chase pressure, or gate idle check. */
    fun onPressureFired(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase == SessionPhase.IDLE || s.phase == SessionPhase.DONE) return

        if (s.phase == SessionPhase.GATE) {
            val stillSolving =
                s.gateFocused && s.gateQuietUntil > System.currentTimeMillis()
            if (stillSolving) {
                scheduleNextPressure(context, s)
                return
            }
            onGateAbandoned(context)
            return
        }

        if (s.phase == SessionPhase.RELAPSE_WAIT || s.phase == SessionPhase.RELAPSE) {
            recoverOverdue(context)
            return
        }

        // Clear Safe Silence on the same write as pressure progress.
        var next = when (s.phase) {
            SessionPhase.CHASE -> s.copy(
                chaseRemaining = (s.chaseRemaining - 1).coerceAtLeast(0),
                safeSilence = false
            )
            SessionPhase.RINGING -> s.copy(safeSilence = false)
            else -> s.copy(safeSilence = false)
        }

        if (next.phase == SessionPhase.CHASE && next.chaseRemaining <= 0) {
            val cycles = next.chaseCycles + 1
            if (cycles >= MAX_CHASE_CYCLES) {
                Log.i(TAG, "chase cap reached ($MAX_CHASE_CYCLES cycles) — ending session")
                endAbandonedSession(context)
                return
            }
            next = next.copy(chaseRemaining = CHASE_COUNT, chaseCycles = cycles)
        }

        DayStartStore.saveSession(context, next)
        Log.i(
            TAG,
            "pressure fired phase=${next.phase} chase=${next.chaseRemaining} cycles=${next.chaseCycles} silenceCleared=true"
        )
        DayStartService.startRinging(context)
        scheduleNextPressure(context, next)
    }

    /** Stop ringing/FGS after max chase so an abandoned morning cannot drain forever. */
    private fun endAbandonedSession(context: Context) {
        DayStartScheduler.cancelAllDynamic(context)
        DayStartService.stopRinging(context)
        AlarmWakeLock.release()
        DayStartStore.clearSession(context)
        rescheduleEnabledAlarms(context)
    }

    fun openGate(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase == SessionPhase.IDLE || s.phase == SessionPhase.DONE) return
        if (s.phase == SessionPhase.RELAPSE || s.phase == SessionPhase.RELAPSE_WAIT) return
        val now = System.currentTimeMillis()
        val gated = s.copy(
            phase = SessionPhase.GATE,
            gateFocused = true,
            safeSilence = false,
            gateQuietUntil = now + GATE_IDLE_MS
        )
        DayStartStore.saveSession(context, gated)
        DayStartService.pauseForGate(context)
        armPressure(context, now + RING_INTERVAL_MS, gated)
    }

    fun touchGate(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase != SessionPhase.GATE) return
        DayStartStore.saveSession(
            context,
            s.copy(
                gateFocused = true,
                gateQuietUntil = System.currentTimeMillis() + GATE_IDLE_MS
            )
        )
    }

    fun onGateAbandoned(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase != SessionPhase.GATE) return
        enterChase(context, s, resetChallenges = true)
    }

    fun setGateFocused(context: Context, focused: Boolean) {
        val s = DayStartStore.session(context)
        if (s.phase != SessionPhase.GATE) return
        val now = System.currentTimeMillis()
        val updated = s.copy(
            gateFocused = focused,
            gateQuietUntil = if (focused) now + GATE_IDLE_MS else 0L
        )
        DayStartStore.saveSession(context, updated)
        if (focused) {
            DayStartService.pauseForGate(context)
        } else {
            armPressure(context, now + RING_INTERVAL_MS, updated)
        }
    }

    fun safeSilence(context: Context): Boolean {
        val s = DayStartStore.session(context)
        if (!s.mode.allowsSafeSilence) return false
        if (s.phase != SessionPhase.RINGING) return false
        if (s.safeSilence) return false // already muted this window
        if (s.safeSilenceUsed >= MAX_SAFE_SILENCE) return false

        val at = System.currentTimeMillis() + RING_INTERVAL_MS
        val muted = s.copy(
            safeSilence = true,
            safeSilenceUsed = s.safeSilenceUsed + 1,
            nextRingAt = at
        )
        DayStartStore.saveSession(context, muted)
        DayStartService.muteCurrent(context)
        DayStartScheduler.schedulePressure(context, at)
        Log.i(TAG, "safe silence used=${muted.safeSilenceUsed} nextRingAt=$at")
        return true
    }

    fun onRingStoppedWithoutWake(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase == SessionPhase.IDLE || s.phase == SessionPhase.DONE) return
        if (s.phase == SessionPhase.GATE || s.phase == SessionPhase.RELAPSE ||
            s.phase == SessionPhase.RELAPSE_WAIT
        ) return
        enterChase(context, s, resetChallenges = false, stopSoundFirst = true)
    }

    fun onChallengeAdvanced(context: Context) {
        val s = DayStartStore.session(context)
        val nextIndex = s.challengeIndex + 1
        if (nextIndex >= s.mode.challenges.size) {
            onGateCleared(context)
        } else {
            DayStartStore.saveSession(context, s.copy(challengeIndex = nextIndex))
            touchGate(context)
        }
    }

    fun onGateCleared(context: Context) {
        DayStartScheduler.cancelAllDynamic(context)
        DayStartService.stopRinging(context)
        val deadlineWait = System.currentTimeMillis() + RELAPSE_WAIT_MS
        DayStartStore.saveSession(
            context,
            DayStartStore.session(context).copy(
                phase = SessionPhase.RELAPSE_WAIT,
                chaseRemaining = 0,
                gateFocused = false,
                safeSilence = false,
                gateQuietUntil = 0L,
                nextRingAt = 0L,
                relapseDeadlineAt = deadlineWait
            )
        )
        DayStartScheduler.scheduleRelapse(context, deadlineWait)
    }

    fun onRelapsePrompt(context: Context) {
        val s = DayStartStore.session(context)
        if (s.phase != SessionPhase.RELAPSE_WAIT && s.phase != SessionPhase.RELAPSE) return
        val deadline = System.currentTimeMillis() + RELAPSE_WINDOW_MS
        DayStartStore.saveSession(
            context,
            s.copy(
                phase = SessionPhase.RELAPSE,
                relapseDeadlineAt = deadline,
                safeSilence = false
            )
        )
        DayStartService.startRinging(context, relapse = true)
        DayStartScheduler.scheduleRelapseTimeout(context, deadline)
    }

    fun onRelapseConfirmed(context: Context) {
        DayStartScheduler.cancelAllDynamic(context)
        DayStartService.stopRinging(context)
        DayStartStore.clearSession(context)
        rescheduleEnabledAlarms(context)
    }

    fun onRelapseFailed(context: Context) {
        val s = DayStartStore.session(context)
        enterChase(context, s, resetChallenges = true)
    }

    /**
     * Emergency only — must not be one-tap from Wake Gate.
     * Returns false if phrase doesn't match.
     */
    fun emergencyHalt(context: Context, phrase: String): Boolean {
        if (phrase.trim() != "STOP DAYSTART") return false
        DayStartScheduler.cancelAllDynamic(context)
        DayStartService.stopRinging(context)
        AlarmWakeLock.release()
        DayStartStore.clearSession(context)
        rescheduleEnabledAlarms(context)
        return true
    }

    fun rescheduleEnabledAlarms(context: Context) {
        DayStartScheduler.cancelAllAlarmClocks(context)
        DayStartStore.alarms(context).filter { it.enabled }.forEach {
            DayStartScheduler.scheduleAlarmClock(context, it)
        }
        recoverOverdue(context)
    }

    /** Fix stuck sessions after reboot / missed deadlines / OEM alarm drops. */
    fun recoverOverdue(context: Context) {
        val s = DayStartStore.session(context)
        val now = System.currentTimeMillis()
        when (s.phase) {
            SessionPhase.RELAPSE_WAIT -> {
                if (s.relapseDeadlineAt <= now) {
                    onRelapsePrompt(context)
                } else {
                    DayStartScheduler.scheduleRelapse(context, s.relapseDeadlineAt)
                }
            }
            SessionPhase.RELAPSE -> {
                if (s.relapseDeadlineAt <= now) {
                    onRelapseFailed(context)
                } else {
                    DayStartScheduler.scheduleRelapseTimeout(context, s.relapseDeadlineAt)
                    DayStartService.startRinging(context, relapse = true)
                }
            }
            SessionPhase.RINGING, SessionPhase.CHASE, SessionPhase.GATE -> {
                if (s.phase == SessionPhase.GATE &&
                    !(s.gateFocused && s.gateQuietUntil > now)
                ) {
                    onGateAbandoned(context)
                    return
                }

                val quietGate =
                    s.phase == SessionPhase.GATE &&
                        s.gateFocused &&
                        s.gateQuietUntil > now

                // Stuck Safe Silence after process death / missed re-ring → force clear & ring
                val silenceExpired =
                    s.safeSilence && (s.nextRingAt <= 0L || s.nextRingAt <= now)
                val ringOverdue = s.nextRingAt in 1..now

                if (silenceExpired || ringOverdue) {
                    val cleared = s.copy(safeSilence = false)
                    DayStartStore.saveSession(context, cleared)
                    if (!quietGate) {
                        DayStartService.startRinging(context)
                    }
                    scheduleNextPressure(context, cleared)
                    return
                }

                scheduleNextPressure(context, s)
                if (!quietGate && !s.safeSilence) {
                    DayStartService.startRinging(context)
                }
            }
            else -> Unit
        }
    }

    private fun enterChase(
        context: Context,
        s: WakeSession,
        resetChallenges: Boolean,
        stopSoundFirst: Boolean = false
    ) {
        if (stopSoundFirst) DayStartService.stopRinging(context)
        val now = System.currentTimeMillis()
        val at = now + RING_INTERVAL_MS
        val chased = s.copy(
            phase = SessionPhase.CHASE,
            chaseRemaining = CHASE_COUNT,
            chaseCycles = 0,
            challengeIndex = if (resetChallenges) 0 else s.challengeIndex,
            gateFocused = false,
            safeSilence = false,
            gateQuietUntil = 0L,
            relapseDeadlineAt = 0L,
            nextRingAt = at
        )
        DayStartStore.saveSession(context, chased)
        if (!stopSoundFirst) {
            DayStartService.startRinging(context)
            DayStartScheduler.schedulePressure(context, at)
        } else {
            // Quiet briefly, then chase re-rings
            DayStartScheduler.schedulePressure(context, at)
        }
    }

    private fun scheduleNextPressure(context: Context, session: WakeSession) {
        if (!isWithinWakeWindow()) return
        val now = System.currentTimeMillis()
        when (session.phase) {
            // Continuous ring — only re-arm if muted (Safe Silence window)
            SessionPhase.RINGING -> {
                if (session.safeSilence && session.nextRingAt > now) {
                    DayStartScheduler.schedulePressure(context, session.nextRingAt)
                }
            }
            SessionPhase.CHASE, SessionPhase.GATE -> {
                armPressure(context, now + RING_INTERVAL_MS, session)
            }
            else -> Unit
        }
    }

    private fun armPressure(context: Context, atMs: Long, session: WakeSession) {
        DayStartStore.saveSession(context, session.copy(nextRingAt = atMs))
        DayStartScheduler.schedulePressure(context, atMs)
    }
}
