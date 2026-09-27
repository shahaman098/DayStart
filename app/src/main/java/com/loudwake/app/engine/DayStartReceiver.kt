package com.loudwake.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.loudwake.app.AlarmActivity
import com.loudwake.app.RelapseActivity
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.SessionPhase

class DayStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive action=$action")
        val pending = goAsync()
        try {
            when (action) {
                ACTION_ALARM -> handleAlarm(context, intent)
                ACTION_PRESSURE, ACTION_BURST_LEGACY -> handlePressure(context)
                ACTION_RELAPSE -> {
                    AlarmWakeLock.acquire(context)
                    WakeEngine.onRelapsePrompt(context)
                    launchFullScreen(context, RelapseActivity::class.java)
                }
                ACTION_RELAPSE_TIMEOUT -> {
                    AlarmWakeLock.acquire(context)
                    WakeEngine.onRelapseFailed(context)
                    launchFullScreen(context, AlarmActivity::class.java)
                }
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED -> {
                    DayStartStore.ensurePersisted(context)
                    WakeEngine.rescheduleEnabledAlarms(context)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "receiver failed", t)
        } finally {
            pending.finish()
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        AlarmWakeLock.acquire(context)
        DayStartStore.ensurePersisted(context)
        val id = intent.getStringExtra(EXTRA_ALARM_ID)
        var alarm = id?.let { DayStartStore.alarm(context, it) }
        if (alarm == null || !alarm.enabled) {
            // Never silent-fail: fire first enabled alarm (or any morning alarm)
            alarm = DayStartStore.alarms(context).firstOrNull { it.enabled }
                ?: DayStartStore.alarms(context).firstOrNull()
            Log.w(TAG, "alarm id=$id missing/disabled; fallback=${alarm?.id}")
        }
        if (alarm == null) {
            Log.e(TAG, "no alarms available to fire")
            return
        }
        if (alarm.repeatDaily) {
            // Re-arm tomorrow first so a crash mid-wake doesn't lose the series
            DayStartScheduler.scheduleAlarmClock(context, alarm)
        } else {
            // One-shot: turn off after this fire
            val once = alarm.copy(enabled = false)
            DayStartStore.upsert(context, once)
            DayStartScheduler.cancelAlarmClock(context, once.id)
            Log.i(TAG, "one-shot alarm ${once.id} disabled after fire")
        }
        WakeEngine.startFromAlarm(context, alarm)
        // Sound/FGS first — activity start is often blocked in background on ColorOS
        DayStartService.startRinging(context)
        launchFullScreen(context, AlarmActivity::class.java)
    }

    private fun handlePressure(context: Context) {
        AlarmWakeLock.acquire(context)
        WakeEngine.onPressureFired(context)
        val s = DayStartStore.session(context)
        val quietSolving =
            s.phase == SessionPhase.GATE &&
                s.gateFocused &&
                s.gateQuietUntil > System.currentTimeMillis()
        if (!quietSolving) {
            DayStartService.startRinging(context)
            launchFullScreen(context, AlarmActivity::class.java)
        }
    }

    private fun launchFullScreen(context: Context, cls: Class<*>) {
        try {
            val i = Intent(context, cls).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(i)
        } catch (t: Throwable) {
            Log.w(TAG, "startActivity blocked — relying on FSI notification", t)
        }
    }

    companion object {
        private const val TAG = "DayStartReceiver"
        const val ACTION_ALARM = "com.loudwake.app.ACTION_DAYSTART_ALARM"
        const val ACTION_PRESSURE = "com.loudwake.app.ACTION_DAYSTART_PRESSURE"
        /** Pre-burst-removal action — still handled so mid-upgrade alarms don't no-op. */
        const val ACTION_BURST_LEGACY = "com.loudwake.app.ACTION_DAYSTART_BURST"
        const val ACTION_RELAPSE = "com.loudwake.app.ACTION_DAYSTART_RELAPSE"
        const val ACTION_RELAPSE_TIMEOUT = "com.loudwake.app.ACTION_DAYSTART_RELAPSE_TIMEOUT"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
