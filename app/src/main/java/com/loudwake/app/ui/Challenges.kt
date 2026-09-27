package com.loudwake.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.loudwake.app.MathChallengeFactory
import com.loudwake.app.data.ChallengeKind
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun ChallengeHost(
    kind: ChallengeKind,
    wakePhrase: String,
    onPassed: () -> Unit
) {
    when (kind) {
        ChallengeKind.MATH -> MathChallengeUI(onPassed)
        ChallengeKind.PHRASE -> PhraseChallengeUI(wakePhrase, onPassed)
        ChallengeKind.SHAKE -> ShakeChallengeUI(onPassed)
        ChallengeKind.STEPS -> StepsChallengeUI(onPassed)
        ChallengeKind.MEMORY -> MemoryChallengeUI(onPassed)
    }
}

@Composable
private fun MathChallengeUI(onPassed: () -> Unit) {
    var challenge by remember { mutableStateOf(MathChallengeFactory.next()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), frosted = false) {
        Text("Math Challenge", style = MaterialTheme.typography.titleMedium)
        Text(challenge.prompt, style = MaterialTheme.typography.headlineMedium)
        DsTextField(
            input,
            {
                input = it.filter { ch -> ch.isDigit() || ch == '-' }
                error = false
            },
            "Answer"
        )
        if (error) {
            Text("Wrong — try again", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton("Submit") {
            val ans = input.toIntOrNull()
            if (ans == challenge.answer) onPassed()
            else {
                error = true
                challenge = MathChallengeFactory.next()
                input = ""
            }
        }
    }
}

@Composable
private fun PhraseChallengeUI(phrase: String, onPassed: () -> Unit) {
    var input by remember { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth(), frosted = false) {
        Text("Type Phrase", style = MaterialTheme.typography.titleMedium)
        Text("Type exactly:", style = MaterialTheme.typography.bodyMedium)
        Text(phrase, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        DsTextField(input, { input = it }, "Wake phrase")
        PrimaryButton("Submit", enabled = input.trim() == phrase, onClick = onPassed)
    }
}

@Composable
private fun ShakeChallengeUI(onPassed: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (done) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = sqrt(x * x + y * y + z * z)
                if (mag > 14f) {
                    main.post {
                        progress = (progress + 0.04f).coerceAtMost(1f)
                        if (progress >= 1f && !done) {
                            done = true
                            onPassed()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    GlassCard(Modifier.fillMaxWidth(), frosted = false) {
        Text("Shake to Wake", style = MaterialTheme.typography.titleMedium)
        Text("Shake until the meter fills", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StepsChallengeUI(onPassed: () -> Unit) {
    val context = LocalContext.current
    var steps by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var activityGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> activityGranted = granted }
    val target = 25
    val main = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        if (!activityGranted && Build.VERSION.SDK_INT >= 29) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    DisposableEffect(activityGranted) {
        if (!activityGranted) {
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastMag = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (done) return
                if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    main.post {
                        steps += 1
                        if (steps >= target && !done) {
                            done = true
                            onPassed()
                        }
                    }
                } else {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val mag = sqrt(x * x + y * y + z * z)
                    if (lastMag > 0 && kotlin.math.abs(mag - lastMag) > 2.8f) {
                        main.post {
                            steps += 1
                            if (steps >= target && !done) {
                                done = true
                                onPassed()
                            }
                        }
                    }
                    lastMag = mag
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (detector != null) {
            sm.registerListener(listener, detector, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (accel != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    GlassCard(Modifier.fillMaxWidth(), frosted = false) {
        Text("Walk It Off", style = MaterialTheme.typography.titleMedium)
        Text(
            if (activityGranted) "Take $target steps"
            else "Allow physical activity permission to count steps",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("$steps / $target", style = MaterialTheme.typography.headlineMedium)
        LinearProgressIndicator(
            progress = { (steps / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (!activityGranted) {
            PrimaryButton("Grant activity permission") {
                if (Build.VERSION.SDK_INT >= 29) {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }
    }
}

// Intentionally independent of the app theme (ember/charcoal): these are Simon-says tile
// colors, and separability between them is a gameplay/accessibility requirement, not brand
// chrome. Collapsing them into the ember/charcoal family would make the memory challenge
// harder to play, not more "on brand" — kept as-is by design during the visual redesign.
private val MemoryColors = listOf(
    Color(0xFFE63946),
    Color(0xFF457B9D),
    Color(0xFF2A9D8F),
    Color(0xFFF4A261)
)

@Composable
private fun MemoryChallengeUI(onPassed: () -> Unit) {
    val sequence = remember { List(4) { Random.nextInt(MemoryColors.size) } }
    var showing by remember { mutableStateOf(true) }
    var showIndex by remember { mutableIntStateOf(-1) }
    val input = remember { mutableStateListOf<Int>() }
    var fail by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showing = true
        for (i in sequence.indices) {
            showIndex = i
            kotlinx.coroutines.delay(550)
            showIndex = -1
            kotlinx.coroutines.delay(180)
        }
        showing = false
    }

    GlassCard(Modifier.fillMaxWidth(), frosted = false) {
        Text("Memory Match", style = MaterialTheme.typography.titleMedium)
        Text(
            if (showing) "Watch the sequence…" else "Tap the colors in order",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MemoryColors.forEachIndexed { idx, color ->
                val lit = showing && showIndex >= 0 && sequence[showIndex] == idx
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (lit) color else color.copy(alpha = 0.45f))
                        .clickable(enabled = !showing) {
                            input.add(idx)
                            val pos = input.lastIndex
                            if (sequence[pos] != idx) {
                                fail = true
                                input.clear()
                            } else if (input.size == sequence.size) {
                                onPassed()
                            }
                        }
                )
            }
        }
        if (fail) Text("Missed — try again from start", color = MaterialTheme.colorScheme.error)
        Text("Progress: ${input.size}/${sequence.size}", style = MaterialTheme.typography.labelSmall)
    }
}

