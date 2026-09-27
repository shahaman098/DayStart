package com.loudwake.app

import android.app.KeyguardManager
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.SessionPhase
import com.loudwake.app.engine.AlarmWakeLock
import com.loudwake.app.engine.WakeEngine
import com.loudwake.app.ui.CoachSol
import com.loudwake.app.ui.DisplayHeroStyle
import com.loudwake.app.ui.MissionTheme
import com.loudwake.app.ui.MissionCard
import com.loudwake.app.ui.OutfitFamily
import com.loudwake.app.ui.PrimaryButton
import com.loudwake.app.ui.SolExpression
import com.loudwake.app.ui.SurfaceBackground
import kotlinx.coroutines.delay

class RelapseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        turnScreenOnAndUnlock()
        AlarmWakeLock.acquire(this)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnAndUnlock()
        // singleInstance — Compose already polling session; force redraw path
        render()
    }

    override fun onResume() {
        super.onResume()
        turnScreenOnAndUnlock()
    }

    override fun onDestroy() {
        AlarmWakeLock.release()
        super.onDestroy()
    }

    private fun render() {
        val activity = this
        setContent {
            MissionTheme {
                SurfaceBackground(useAtmosphere = true) {
                    RelapseHost(
                        onAwake = {
                            WakeEngine.onRelapseConfirmed(activity)
                            Toast.makeText(activity, "Morning done. Stay up.", Toast.LENGTH_SHORT)
                                .show()
                            activity.finish()
                        },
                        onTimeout = {
                            WakeEngine.onRelapseFailed(activity)
                            Toast.makeText(
                                activity,
                                "Relapse — chase restarting",
                                Toast.LENGTH_SHORT
                            ).show()
                            activity.startActivity(Intent(activity, AlarmActivity::class.java))
                            activity.finish()
                        },
                        onIdle = { activity.finish() }
                    )
                }
            }
        }
    }

    private fun turnScreenOnAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }
}

@Composable
private fun RelapseHost(
    onAwake: () -> Unit,
    onTimeout: () -> Unit,
    onIdle: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableStateOf(DayStartStore.session(context).phase) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var timedOut by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    // Sol's biggest payoff moment — a beat of "you did it" before the bare finish(),
    // instead of the ring just silently going away.
    LaunchedEffect(cleared) {
        if (cleared) {
            delay(1200L)
            onAwake()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (cleared) {
                delay(200L)
                continue
            }
            val s = DayStartStore.session(context)
            phase = s.phase
            val now = System.currentTimeMillis()
            when (s.phase) {
                SessionPhase.RELAPSE_WAIT -> {
                    val leftMs = s.relapseDeadlineAt - now
                    if (leftMs <= 0L) {
                        WakeEngine.onRelapsePrompt(context)
                        // Loop continues into RELAPSE
                    } else {
                        secondsLeft = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(0)
                    }
                }
                SessionPhase.RELAPSE -> {
                    val leftMs = s.relapseDeadlineAt - now
                    if (leftMs <= 0L) {
                        if (!timedOut) {
                            timedOut = true
                            onTimeout()
                        }
                        return@LaunchedEffect
                    }
                    secondsLeft = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(0)
                }
                SessionPhase.IDLE, SessionPhase.DONE -> {
                    onIdle()
                    return@LaunchedEffect
                }
                else -> {
                    // Chase/gate restarted elsewhere
                    onIdle()
                    return@LaunchedEffect
                }
            }
            delay(200L)
        }
    }

    if (cleared) {
        MissionClearedScreen()
    } else {
        when (phase) {
            SessionPhase.RELAPSE_WAIT -> RelapseWaitScreen(secondsLeft = secondsLeft)
            SessionPhase.RELAPSE -> RelapsePromptScreen(
                secondsLeft = secondsLeft,
                onAwake = { cleared = true }
            )
            else -> RelapseWaitScreen(secondsLeft = secondsLeft)
        }
    }
}

@Composable
private fun MissionClearedScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CoachSol(expression = SolExpression.PROUD, size = 120.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Morning cleared",
            style = DisplayHeroStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "Stay up. You did the work.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RelapseWaitScreen(secondsLeft: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            "RELAPSE GUARD",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            "Stay awake",
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "Check-in coming — don’t go back to bed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
        MissionCard(Modifier.fillMaxWidth()) {
            Text(
                formatCountdown(secondsLeft),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 80.sp,
                    fontFeatureSettings = "tnum, lnum",
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                "until check-in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RelapsePromptScreen(
    secondsLeft: Int,
    onAwake: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "RELAPSE GUARD",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            "Still awake?",
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 42.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        MissionCard(Modifier.fillMaxWidth()) {
            Text(
                formatCountdown(secondsLeft),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 80.sp,
                    fontFeatureSettings = "tnum, lnum",
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                "seconds left",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                "Tap I’m Awake or chase restarts and Wake Gate resets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("I’m Awake", onClick = onAwake)
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "%d:%02d".format(m, r) else "%02d".format(r)
}
