package com.loudwake.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.SessionPhase
import com.loudwake.app.data.WakeSession
import com.loudwake.app.engine.AlarmWakeLock
import com.loudwake.app.engine.WakeEngine
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.loudwake.app.ui.ChallengeHost
import com.loudwake.app.ui.DisplayHeroStyle
import com.loudwake.app.ui.GateProgress
import com.loudwake.app.ui.MissionCard
import com.loudwake.app.ui.MissionTheme
import com.loudwake.app.ui.OutfitFamily
import com.loudwake.app.ui.PrimaryButton
import com.loudwake.app.ui.SecondaryButton
import com.loudwake.app.ui.SurfaceBackground
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            val s = DayStartStore.session(this@AlarmActivity)
            if (s.phase == SessionPhase.GATE) {
                WakeEngine.onGateAbandoned(this@AlarmActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        turnScreenOnAndUnlock()
        AlarmWakeLock.acquire(this)
        val activity = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        setContent {
            MissionTheme {
                var session by remember { mutableStateOf(DayStartStore.session(activity)) }
                var inGate by remember {
                    mutableStateOf(
                        session.phase == SessionPhase.GATE || session.gateFocused
                    )
                }
                var lastInteractAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

                LaunchedEffect(inGate) {
                    setKeepScreen(!inGate)
                    if (!inGate) return@LaunchedEffect
                    lastInteractAt = System.currentTimeMillis()
                    while (inGate) {
                        delay(1_000)
                        val idle = System.currentTimeMillis() - lastInteractAt
                        if (idle >= WakeEngine.GATE_IDLE_MS) {
                            WakeEngine.onGateAbandoned(activity)
                            session = DayStartStore.session(activity)
                            inGate = false
                            Toast.makeText(
                                activity,
                                "Wake Gate abandoned — chase restarting",
                                Toast.LENGTH_SHORT
                            ).show()
                            break
                        }
                    }
                }

                // Keep UI in sync with silence / chase pressure
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1_000)
                        val latest = DayStartStore.session(activity)
                        if (latest.phase != session.phase ||
                            latest.chaseRemaining != session.chaseRemaining ||
                            latest.challengeIndex != session.challengeIndex ||
                            latest.safeSilence != session.safeSilence ||
                            latest.safeSilenceUsed != session.safeSilenceUsed ||
                            latest.nextRingAt != session.nextRingAt
                        ) {
                            session = latest
                            if (latest.phase == SessionPhase.CHASE ||
                                latest.phase == SessionPhase.RINGING
                            ) {
                                inGate = false
                            }
                        }
                    }
                }

                SurfaceBackground(useAtmosphere = true) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(inGate) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        lastInteractAt = System.currentTimeMillis()
                                        if (inGate) WakeEngine.touchGate(activity)
                                    }
                                }
                            }
                    ) {
                        // Only show gate while session is still GATE (not stuck after chase)
                        if (inGate && session.phase == SessionPhase.GATE) {
                            WakeGateScreen(
                                session = session,
                                onPassedChallenge = {
                                    lastInteractAt = System.currentTimeMillis()
                                    WakeEngine.touchGate(activity)
                                    WakeEngine.onChallengeAdvanced(activity)
                                    session = DayStartStore.session(activity)
                                    if (session.phase == SessionPhase.RELAPSE_WAIT) {
                                        Toast.makeText(
                                            activity,
                                            "Wake Gate cleared. Relapse Guard armed.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        activity.startActivity(
                                            Intent(activity, RelapseActivity::class.java).addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            )
                                        )
                                        activity.finish()
                                    }
                                }
                            )
                        } else {
                            val silenceLeft =
                                (WakeEngine.MAX_SAFE_SILENCE - session.safeSilenceUsed)
                                    .coerceAtLeast(0)
                            val mutedNow = session.safeSilence &&
                                session.nextRingAt > System.currentTimeMillis()
                            FiringScreen(
                                phase = session.phase,
                                chaseRemaining = session.chaseRemaining,
                                modeTitle = session.mode.title,
                                muted = mutedNow,
                                nextRingAt = session.nextRingAt,
                                allowsSafeSilence = session.mode.allowsSafeSilence &&
                                    session.phase == SessionPhase.RINGING &&
                                    silenceLeft > 0 &&
                                    !mutedNow,
                                silenceLeft = silenceLeft,
                                onWakeGate = {
                                    WakeEngine.openGate(activity)
                                    inGate = true
                                    session = DayStartStore.session(activity)
                                    lastInteractAt = System.currentTimeMillis()
                                },
                                onSafeSilence = {
                                    val ok = WakeEngine.safeSilence(activity)
                                    Toast.makeText(
                                        activity,
                                        if (ok) {
                                            activity.getString(R.string.safe_silence_toast)
                                        } else {
                                            "Safe Silence unavailable"
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    session = DayStartStore.session(activity)
                                },
                                onStopRing = {
                                    WakeEngine.onRingStoppedWithoutWake(activity)
                                    Toast.makeText(
                                        activity,
                                        "Chase armed — finish Wake Gate",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    activity.finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        AlarmWakeLock.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = DayStartStore.session(this)
        if (s.phase == SessionPhase.GATE) {
            WakeEngine.onGateAbandoned(this)
        }
    }

    override fun onResume() {
        super.onResume()
        turnScreenOnAndUnlock()
        val s = DayStartStore.session(this)
        if (s.phase == SessionPhase.GATE) {
            WakeEngine.touchGate(this)
            WakeEngine.setGateFocused(this, true)
            setKeepScreen(false)
        } else {
            setKeepScreen(true)
        }
    }

    override fun onPause() {
        val s = DayStartStore.session(this)
        if (s.phase == SessionPhase.GATE) {
            WakeEngine.setGateFocused(this, false)
        }
        super.onPause()
    }

    private fun turnScreenOnAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    private fun setKeepScreen(keep: Boolean) {
        turnScreenOnAndUnlock()
        if (keep) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun FiringScreen(
    phase: SessionPhase,
    chaseRemaining: Int,
    modeTitle: String,
    muted: Boolean,
    nextRingAt: Long,
    allowsSafeSilence: Boolean,
    silenceLeft: Int,
    onWakeGate: () -> Unit,
    onSafeSilence: () -> Unit,
    onStopRing: () -> Unit
) {
    val title = when {
        muted -> "Muted"
        phase == SessionPhase.CHASE -> "Chase"
        else -> "Wake Up"
    }
    var secondsLeft by remember(nextRingAt, muted) {
        mutableIntStateOf(
            ((nextRingAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        )
    }
    LaunchedEffect(nextRingAt, muted) {
        if (!muted) return@LaunchedEffect
        while (true) {
            secondsLeft =
                ((nextRingAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            delay(250)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "DAYSTART",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            title,
            style = DisplayHeroStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
        MissionCard(Modifier.fillMaxWidth()) {
            Text(
                modeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (muted) {
                Text(
                    "Safe Silence — rings again in ${secondsLeft}s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else if (phase == SessionPhase.CHASE) {
                Text(
                    "$chaseRemaining chase left",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                if (muted) {
                    "Alarm still returns. Finish Wake Gate to end the morning."
                } else {
                    "Dismissing the ring is not waking up."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Open Wake Gate", onClick = onWakeGate)
        if (allowsSafeSilence) {
            SecondaryButton("Safe Silence ($silenceLeft left)", onClick = onSafeSilence)
        }
        if (!muted) {
            SecondaryButton("Stop this ring", onClick = onStopRing)
        }
    }
}

@Composable
private fun WakeGateScreen(
    session: WakeSession,
    onPassedChallenge: () -> Unit
) {
    val kinds = session.mode.challenges
    val total = kinds.size.coerceAtLeast(1)
    val index = session.challengeIndex.coerceIn(0, (kinds.size - 1).coerceAtLeast(0))
    val kind = kinds.getOrNull(index)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "WAKE GATE",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            session.mode.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        GateProgress(
            current = index + 1,
            total = total,
            label = "Challenge ${index + 1} of $total"
        )
        Text(
            "Stay active — idle or sleep restarts chase. No skip.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (kind != null) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                        .togetherWith(
                            fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 4 }
                        )
                },
                label = "challengeTransition"
            ) { i ->
                val currentKind = kinds.getOrNull(i) ?: kind
                ChallengeHost(
                    kind = currentKind,
                    wakePhrase = session.wakePhrase,
                    onPassed = onPassedChallenge
                )
            }
        } else {
            PrimaryButton("Complete", onClick = onPassedChallenge)
        }
    }
}
