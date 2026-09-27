package com.loudwake.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.loudwake.app.BuildConfig
import com.loudwake.app.data.AlarmItem
import com.loudwake.app.data.AlarmTone
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.ParsedVoiceAlarm
import com.loudwake.app.data.SessionPhase
import com.loudwake.app.data.SystemSound
import com.loudwake.app.data.SystemSounds
import com.loudwake.app.data.VoiceAlarmParser
import com.loudwake.app.data.VoiceStyle
import com.loudwake.app.data.WakeMode
import com.loudwake.app.engine.DayStartScheduler
import com.loudwake.app.engine.TonePlayer
import com.loudwake.app.engine.VoiceAlarmSpeech
import com.loudwake.app.engine.WakeEngine
import com.loudwake.app.ui.Chip
import com.loudwake.app.ui.ClockAlarmRow
import com.loudwake.app.ui.CoachSol
import com.loudwake.app.ui.DangerTextButton
import com.loudwake.app.ui.DayStartTheme
import com.loudwake.app.ui.DsSwitch
import com.loudwake.app.ui.DsTextField
import com.loudwake.app.ui.GlassCard
import com.loudwake.app.ui.ModeBadge
import com.loudwake.app.ui.OutfitFamily
import com.loudwake.app.ui.PrefRow
import com.loudwake.app.ui.PrimaryButton
import com.loudwake.app.ui.SecondaryButton
import com.loudwake.app.ui.SectionHeader
import com.loudwake.app.ui.SolExpression
import com.loudwake.app.ui.SurfaceBackground
import com.loudwake.app.ui.TimeRoller
import com.loudwake.app.ui.WakeClockFace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private var pendingVoiceListen = false
    private var pendingDemoVoiceTranscript: String? = null
    private var onVoiceTranscript: ((String) -> Unit)? = null
    private var onVoiceListening: ((Boolean) -> Unit)? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingVoiceListen) {
            pendingVoiceListen = false
            launchVoiceRecognizer()
        } else if (!granted) {
            pendingVoiceListen = false
            onVoiceListening?.invoke(false)
            Toast.makeText(this, "Microphone permission needed for Voice set", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onVoiceListening?.invoke(false)
        val transcript = VoiceAlarmSpeech.transcriptFromResult(result.resultCode, result.data)
        if (transcript == null) {
            Toast.makeText(this, "No speech captured", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        onVoiceTranscript?.invoke(transcript)
    }

    private var tonePlayer: TonePlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tonePlayer = TonePlayer(this)
        requestRuntimePermissions()
        ensureExactAlarmAccess()
        DayStartStore.ensurePersisted(this)
        WakeEngine.rescheduleEnabledAlarms(this)
        handleDemoExtras(intent)
        val activity = this

        setContent {
            DayStartTheme {
                var alarms by remember { mutableStateOf(DayStartStore.alarms(activity)) }
                var editing by remember { mutableStateOf<AlarmItem?>(null) }
                var creating by remember { mutableStateOf(false) }
                var voiceTranscript by remember { mutableStateOf<String?>(null) }
                var voiceParsed by remember { mutableStateOf<List<ParsedVoiceAlarm>>(emptyList()) }
                var voiceListening by remember { mutableStateOf(false) }
                var nextLabel by remember {
                    mutableStateOf(DayStartScheduler.nextAlarmLabel(activity))
                }
                var exactOk by remember { mutableStateOf(DayStartScheduler.canExact(activity)) }
                var batteryOk by remember { mutableStateOf(isBatteryUnrestricted()) }
                var dndOk by remember { mutableStateOf(isDndAccessGranted()) }
                var showBatteryRationale by remember {
                    mutableStateOf(
                        !batteryOk && !DayStartStore.isBatteryPromptDismissed(activity)
                    )
                }

                fun refreshHome() {
                    alarms = DayStartStore.alarms(activity)
                    nextLabel = DayStartScheduler.nextAlarmLabel(activity)
                    exactOk = DayStartScheduler.canExact(activity)
                    batteryOk = isBatteryUnrestricted()
                    dndOk = isDndAccessGranted()
                    if (batteryOk) showBatteryRationale = false
                }

                fun applyVoiceTranscript(transcript: String) {
                    val parsed = VoiceAlarmParser.parse(transcript)
                    voiceTranscript = transcript
                    voiceParsed = parsed
                    if (parsed.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "Couldn't parse dates/times — try again",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                DisposableEffect(Unit) {
                    onVoiceTranscript = { applyVoiceTranscript(it) }
                    onVoiceListening = { voiceListening = it }
                    val demo = pendingDemoVoiceTranscript
                        ?: activity.intent.getStringExtra(EXTRA_DEMO_VOICE_TRANSCRIPT)
                    if (!demo.isNullOrBlank()) {
                        pendingDemoVoiceTranscript = null
                        activity.intent.removeExtra(EXTRA_DEMO_VOICE_TRANSCRIPT)
                        applyVoiceTranscript(demo)
                    }
                    onDispose {
                        onVoiceTranscript = null
                        onVoiceListening = null
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) refreshHome()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (showBatteryRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            DayStartStore.setBatteryPromptDismissed(activity, true)
                            showBatteryRationale = false
                        },
                        title = { Text(stringResource(R.string.battery_rationale_title)) },
                        text = { Text(stringResource(R.string.battery_rationale_body)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    DayStartStore.setBatteryPromptDismissed(activity, true)
                                    showBatteryRationale = false
                                    requestBatteryUnrestricted()
                                }
                            ) {
                                Text(stringResource(R.string.battery_rationale_allow))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    DayStartStore.setBatteryPromptDismissed(activity, true)
                                    showBatteryRationale = false
                                }
                            ) {
                                Text(stringResource(R.string.battery_rationale_not_now))
                            }
                        }
                    )
                }

                if (voiceListening) {
                    AlertDialog(
                        onDismissRequest = { voiceListening = false },
                        title = { Text("Voice set") },
                        text = {
                            Text(
                                "Listening… say dates and times for your alarms.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { voiceListening = false }) { Text("Cancel") }
                        }
                    )
                }

                if (voiceTranscript != null && voiceParsed.isNotEmpty()) {
                    VoiceSetConfirmDialog(
                        transcript = voiceTranscript!!,
                        parsed = voiceParsed,
                        onDismiss = {
                            voiceTranscript = null
                            voiceParsed = emptyList()
                        },
                        onConfirm = {
                            val template = alarms.lastOrNull { it.enabled } ?: AlarmItem()
                            voiceParsed.forEach { p ->
                                DayStartStore.upsert(activity, p.toAlarmItem(template))
                            }
                            WakeEngine.rescheduleEnabledAlarms(activity)
                            refreshHome()
                            Toast.makeText(
                                activity,
                                "Created ${voiceParsed.size} alarm${if (voiceParsed.size == 1) "" else "s"}",
                                Toast.LENGTH_SHORT
                            ).show()
                            voiceTranscript = null
                            voiceParsed = emptyList()
                        }
                    )
                }

                SurfaceBackground(useAtmosphere = true) {
                    val showEditor = creating || editing != null
                    AnimatedContent(
                        targetState = showEditor,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(200)) { -it / 3 } +
                                            fadeOut(tween(200))
                                    )
                            } else {
                                (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(200)) { it / 3 } +
                                            fadeOut(tween(200))
                                    )
                            }
                        },
                        label = "homeEditorTransition"
                    ) { isEditor ->
                        if (isEditor) {
                            AlarmEditorScreen(
                                initial = editing ?: AlarmItem(),
                                isNew = creating,
                                onPreviewTone = { t, uri ->
                                    tonePlayer?.preview(t, systemUri = uri)
                                },
                                onTest = { alarm ->
                                    if (WakeEngine.hasActiveSession(activity)) {
                                        Toast.makeText(
                                            activity,
                                            "Finish the active wake first",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        WakeEngine.startTest(activity, alarm)
                                        activity.startActivity(
                                            Intent(activity, AlarmActivity::class.java)
                                        )
                                    }
                                },
                                onSave = { item ->
                                    DayStartStore.upsert(activity, item)
                                    if (item.enabled) {
                                        DayStartScheduler.scheduleAlarmClock(activity, item)
                                    } else {
                                        DayStartScheduler.cancelAlarmClock(activity, item.id)
                                    }
                                    WakeEngine.rescheduleEnabledAlarms(activity)
                                    refreshHome()
                                    creating = false
                                    editing = null
                                    Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = { id ->
                                    DayStartScheduler.cancelAlarmClock(activity, id)
                                    DayStartStore.delete(activity, id)
                                    WakeEngine.rescheduleEnabledAlarms(activity)
                                    refreshHome()
                                    creating = false
                                    editing = null
                                },
                                onCancel = {
                                    creating = false
                                    editing = null
                                }
                            )
                        } else {
                            HomeScreen(
                                alarms = alarms,
                                nextAlarmLabel = nextLabel,
                                exactAlarmOk = exactOk,
                                batteryOk = batteryOk,
                                dndOk = dndOk,
                                sessionPhase = DayStartStore.session(activity).phase,
                                onToggle = { alarm, enabled ->
                                    val updated = alarm.copy(enabled = enabled)
                                    DayStartStore.upsert(activity, updated)
                                    if (enabled) {
                                        DayStartScheduler.scheduleAlarmClock(activity, updated)
                                    } else {
                                        DayStartScheduler.cancelAlarmClock(activity, updated.id)
                                    }
                                    WakeEngine.rescheduleEnabledAlarms(activity)
                                    refreshHome()
                                },
                                onEdit = { editing = it },
                                onCreate = { creating = true },
                                onVoiceSet = { beginVoiceSet() },
                                onOpenGate = {
                                    WakeEngine.openGate(activity)
                                    activity.startActivity(
                                        Intent(activity, AlarmActivity::class.java)
                                    )
                                },
                                onOpenRelapse = {
                                    activity.startActivity(
                                        Intent(activity, RelapseActivity::class.java)
                                    )
                                },
                                onEmergencyHalt = { phrase ->
                                    val ok = WakeEngine.emergencyHalt(activity, phrase)
                                    Toast.makeText(
                                        activity,
                                        if (ok) "Emergency stop — session cleared"
                                        else "Type STOP DAYSTART exactly",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ok) refreshHome()
                                    ok
                                },
                                onPermissions = { openReliabilitySettings() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDemoExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        DayStartStore.ensurePersisted(this)
        WakeEngine.rescheduleEnabledAlarms(this)
        handleDemoExtras(intent)
    }

    override fun onDestroy() {
        tonePlayer?.stop()
        super.onDestroy()
    }

    /**
     * Automation / adb hook: Compose text fields often ignore shell `input text` on OEM builds.
     * UI still exposes Emergency stop; this applies the same [WakeEngine.emergencyHalt] path.
     *
     * adb shell am start -n com.loudwake.app/.MainActivity \
     *   --ez demo_emergency_halt true --es demo_halt_phrase "STOP DAYSTART"
     */
    private fun handleDemoExtras(intent: Intent?) {
        // Release-safe: never honor automation extras from arbitrary callers.
        if (!BuildConfig.DEBUG) return
        if (intent == null) return
        val demoVoice = intent.getStringExtra(EXTRA_DEMO_VOICE_TRANSCRIPT)
        if (!demoVoice.isNullOrBlank()) {
            intent.removeExtra(EXTRA_DEMO_VOICE_TRANSCRIPT)
            android.util.Log.e("DayStartDemo", "demo_voice_transcript=$demoVoice")
            pendingDemoVoiceTranscript = demoVoice
            // Defer until Compose has attached onVoiceTranscript (OEM cold-start race).
            window.decorView.post {
                val text = pendingDemoVoiceTranscript ?: return@post
                val sink = onVoiceTranscript
                if (sink != null) {
                    pendingDemoVoiceTranscript = null
                    sink(text)
                }
            }
        }
        // Accept boolean or string "true" — some am/OEM paths stringify extras.
        val halt = intent.getBooleanExtra(EXTRA_DEMO_EMERGENCY_HALT, false) ||
            intent.getStringExtra(EXTRA_DEMO_EMERGENCY_HALT) == "true" ||
            intent.hasExtra(EXTRA_DEMO_EMERGENCY_HALT) &&
            intent.extras?.get(EXTRA_DEMO_EMERGENCY_HALT)?.toString() == "true"
        if (!halt) return
        intent.removeExtra(EXTRA_DEMO_EMERGENCY_HALT)
        val phrase = intent.getStringExtra(EXTRA_DEMO_HALT_PHRASE) ?: "STOP DAYSTART"
        val ok = WakeEngine.emergencyHalt(this, phrase)
        android.util.Log.i("DayStartDemo", "demo_emergency_halt ok=$ok phrase=$phrase")
        Toast.makeText(
            this,
            if (ok) "Emergency stop — session cleared" else "Type STOP DAYSTART exactly",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun beginVoiceSet() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchVoiceRecognizer()
        } else {
            pendingVoiceListen = true
            onVoiceListening?.invoke(true)
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchVoiceRecognizer() {
        onVoiceListening?.invoke(true)
        runCatching {
            speechLauncher.launch(VoiceAlarmSpeech.createListenIntent())
        }.onFailure {
            onVoiceListening?.invoke(false)
            Toast.makeText(this, "Speech recognition unavailable on this device", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun requestRuntimePermissions() {
        // Only ask for notifications up front. Mic / activity are requested
        // when the user starts a challenge or voice set (Play permission justification).
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun ensureExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    /** Opens the system sheet only after the in-app rationale is accepted. */
    private fun requestBatteryUnrestricted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isBatteryUnrestricted()) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    /**
     * Whether we can force Total Silence / strict Focus modes down to "alarms only"
     * ([TonePlayer]'s bypassDndIfAllowed). Without this the OS is still required to let
     * a proper STREAM_ALARM tone through normal Do Not Disturb, but a small number of
     * OEM Focus modes and Total Silence specifically suppress alarms too unless an app
     * holds this permission and uses it to relax the filter.
     */
    private fun isDndAccessGranted(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return true
        return nm.isNotificationPolicyAccessGranted
    }

    /** Opens the system sheet only after the in-app rationale is accepted. */
    private fun requestDndAccess() {
        if (isDndAccessGranted()) return
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun openReliabilitySettings() {
        // Opened only from problem banners (exact alarm / battery / DND), not a permanent menu.
        if (!isBatteryUnrestricted()) {
            requestBatteryUnrestricted()
        }
        if (!isDndAccessGranted()) {
            requestDndAccess()
        }
        // Try ColorOS / Oppo autostart managers first
        val oem = listOf(
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
        for (i in oem) {
            if (runCatching { startActivity(i); true }.getOrDefault(false)) break
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_DEMO_EMERGENCY_HALT = "demo_emergency_halt"
        const val EXTRA_DEMO_HALT_PHRASE = "demo_halt_phrase"
        const val EXTRA_DEMO_VOICE_TRANSCRIPT = "demo_voice_transcript"
    }
}

@Composable
private fun VoiceSetConfirmDialog(
    transcript: String,
    parsed: List<ParsedVoiceAlarm>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dateFmt = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "“$transcript”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                parsed.forEach { p ->
                    val whenLabel = when {
                        p.repeatDaily -> "Daily"
                        p.oneShotDate != null ->
                            runCatching { LocalDate.parse(p.oneShotDate) }.getOrNull()
                                ?.format(dateFmt) ?: p.oneShotDate
                        else -> "Once"
                    }
                    Text(
                        "%s · %02d:%02d · %s".format(p.name, p.hour, p.minute, whenLabel),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Create & arm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Discard") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    alarms: List<AlarmItem>,
    nextAlarmLabel: String?,
    exactAlarmOk: Boolean,
    batteryOk: Boolean,
    dndOk: Boolean,
    sessionPhase: SessionPhase,
    onToggle: (AlarmItem, Boolean) -> Unit,
    onEdit: (AlarmItem) -> Unit,
    onCreate: () -> Unit,
    onVoiceSet: () -> Unit,
    onOpenGate: () -> Unit,
    onOpenRelapse: () -> Unit,
    onEmergencyHalt: (String) -> Boolean,
    onPermissions: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var haltPhrase by remember { mutableStateOf("") }
    var showHalt by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val active = sessionPhase != SessionPhase.IDLE && sessionPhase != SessionPhase.DONE
    val versionLabel = remember {
        runCatching {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
            "v${p.versionName} ($code)"
        }.getOrDefault("DayStart")
    }
    var showSettings by remember { mutableStateOf(false) }
    val noAlarmsLabel = stringResource(R.string.no_alarms_scheduled)
    val nextLabel = nextAlarmLabel ?: noAlarmsLabel
    val nextAlarm = remember(alarms) {
        alarms
            .filter { it.enabled }
            .mapNotNull { alarm -> DayStartScheduler.nextTriggerMs(alarm)?.let { alarm to it } }
            .minByOrNull { it.second }
            ?.first
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoachSol(
                            expression = if (active) SolExpression.ALERT else SolExpression.RESTING,
                            size = 28.dp
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                    append("Day")
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("Start")
                                }
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Settings"
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showSettings,
                        onDismissRequest = { showSettings = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cd_voice_set_short)) },
                            onClick = {
                                showSettings = false
                                onVoiceSet()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Mic, contentDescription = null)
                            }
                        )
                        if (!exactAlarmOk || !batteryOk || !dndOk) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fix_alarm_permissions)) },
                                onClick = {
                                    showSettings = false
                                    onPermissions()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about_title)) },
                            onClick = {
                                showSettings = false
                                showAbout = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(R.string.cd_add_alarm)
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text(stringResource(R.string.about_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(versionLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.about_credits),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.developer_support_email),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse(
                                            "mailto:${context.getString(R.string.developer_support_email)}"
                                        )
                                    )
                                )
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(context.getString(R.string.privacy_policy_url))
                                )
                            )
                        }
                    ) { Text(stringResource(R.string.about_privacy)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAbout = false }) { Text("Close") }
                }
            )
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!exactAlarmOk || !batteryOk || !dndOk) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        if (!exactAlarmOk) {
                            Text(
                                stringResource(R.string.exact_alarm_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPermissions)
                                    .padding(vertical = 6.dp)
                            )
                        }
                        if (!batteryOk) {
                            Text(
                                stringResource(R.string.battery_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPermissions)
                                    .padding(vertical = 6.dp)
                            )
                        }
                        if (!dndOk) {
                            Text(
                                stringResource(R.string.dnd_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPermissions)
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (active) {
                // Session-active hero replaces the next-wake card entirely while active —
                // the alarm "next wake" would point at is the one currently ringing/chasing,
                // so showing both was redundant, not just unstyled.
                item {
                    GlassCard(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        val pulse = rememberInfiniteTransition(label = "armingPulse")
                        val pulseAlpha by pulse.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1600, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "armingPulseAlpha"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                        CircleShape
                                    )
                            )
                            Text(
                                when (sessionPhase) {
                                    SessionPhase.GATE -> "Wake Gate open"
                                    SessionPhase.RELAPSE_WAIT, SessionPhase.RELAPSE -> "Relapse Guard"
                                    SessionPhase.CHASE -> "Chase armed"
                                    SessionPhase.RINGING -> "Alarm ringing"
                                    else -> "Session active"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            when (sessionPhase) {
                                SessionPhase.RELAPSE_WAIT ->
                                    "Stay awake — check-in is coming."
                                SessionPhase.RELAPSE ->
                                    "Tap I’m Awake before chase restarts."
                                SessionPhase.GATE ->
                                    "Finish challenges to clear Wake Gate."
                                else ->
                                    "Open the wake UI — dismissing the ring is not waking up."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (sessionPhase) {
                            SessionPhase.RELAPSE_WAIT, SessionPhase.RELAPSE ->
                                PrimaryButton("Open Relapse Guard", onClick = onOpenRelapse)
                            else ->
                                PrimaryButton("Open Wake Gate", onClick = onOpenGate)
                        }
                        if (!showHalt) {
                            DangerTextButton("Emergency stop…") { showHalt = true }
                        } else {
                            Text(
                                "Type STOP DAYSTART to cancel this morning",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            DsTextField(haltPhrase, { haltPhrase = it }, "STOP DAYSTART")
                            PrimaryButton("Confirm emergency stop") {
                                if (onEmergencyHalt(haltPhrase)) {
                                    showHalt = false
                                    haltPhrase = ""
                                }
                            }
                            SecondaryButton("Cancel") {
                                showHalt = false
                                haltPhrase = ""
                            }
                        }
                    }
                }
            }

            if (!active) {
                item {
                    GlassCard(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (nextAlarm != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                WakeClockFace(
                                    hour = nextAlarm.hour,
                                    minute = nextAlarm.minute,
                                    size = 88.dp
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Next wake",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "%02d:%02d".format(nextAlarm.hour, nextAlarm.minute),
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum, lnum"
                                        )
                                    )
                                    Text(
                                        nextLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ModeBadge(
                                        text = nextAlarm.mode.title,
                                        strict = !nextAlarm.mode.allowsSafeSilence
                                    )
                                }
                            }
                        } else {
                            // Single illustrated empty state — this used to be plain text
                            // rendered twice at once (here and again as a separate list item).
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CoachSol(expression = SolExpression.RESTING, size = 64.dp)
                                Text(noAlarmsLabel, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.empty_alarms_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                SecondaryButton("Add your first alarm", onClick = onCreate)
                            }
                        }
                    }
                }
            }

            items(alarms, key = { it.id }) { alarm ->
                val settleState = remember(alarm.id) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = settleState,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    ClockAlarmRow(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        enabled = alarm.enabled,
                        title = alarm.name,
                        subtitle = listOf(
                            alarm.repeatLabel(),
                            alarm.mode.title,
                            alarm.soundLabel()
                        ).joinToString(" · "),
                        onClick = { onEdit(alarm) },
                        onToggle = { onToggle(alarm, it) }
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorScreen(
    initial: AlarmItem,
    isNew: Boolean,
    onPreviewTone: (AlarmTone, String?) -> Unit,
    onTest: (AlarmItem) -> Unit,
    onSave: (AlarmItem) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemSounds = remember { SystemSounds.list(context) }
    var name by remember { mutableStateOf(initial.name) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var repeatDaily by remember { mutableStateOf(initial.repeatDaily) }
    var oneShotDate by remember {
        mutableStateOf(
            initial.oneShotDate
                ?: if (!initial.repeatDaily) {
                    DayStartScheduler.defaultOneShotDate(initial.hour, initial.minute)
                } else {
                    null
                }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var showVoiceStylePicker by remember { mutableStateOf(false) }
    var showLabelEditor by remember { mutableStateOf(false) }
    var showPhraseEditor by remember { mutableStateOf(false) }
    var tone by remember { mutableStateOf(initial.tone) }
    // New alarms default to the device's own alarm sound — already calibrated for loudness
    // on this hardware, unlike the bundled tones. Existing alarms keep whatever was saved.
    var systemUri by remember {
        mutableStateOf(
            initial.systemSoundUri
                ?: if (isNew) {
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                        ?.toString()
                } else {
                    null
                }
        )
    }
    var voiceEnabled by remember { mutableStateOf(initial.voiceEnabled) }
    var voiceStyle by remember { mutableStateOf(initial.voiceStyle) }
    var mode by remember { mutableStateOf(initial.mode) }
    var phrase by remember { mutableStateOf(initial.wakePhrase) }
    // Sound(s) picked from the device's full ringtone/alarm library via the system picker —
    // covers anything already on the phone beyond our curated shortlist, including tones
    // added by other apps or downloaded music. Not persisted separately; systemUri is the
    // source of truth, this just remembers the human-readable title for display.
    var pickedSound by remember {
        mutableStateOf(
            initial.systemSoundUri
                ?.takeIf { uri -> systemSounds.none { it.uri == uri } }
                ?.let { uri ->
                    val title = runCatching {
                        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
                    }.getOrNull()
                    SystemSound(title ?: "Custom sound", uri)
                }
        )
    }
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.pickedRingtoneUri() ?: return@rememberLauncherForActivityResult
        val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
        pickedSound = SystemSound(title ?: "Custom sound", uri.toString())
        systemUri = uri.toString()
        onPreviewTone(tone, uri.toString())
    }
    fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            )
            systemUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
        }
        runCatching { ringtonePicker.launch(intent) }
            .onFailure {
                Toast.makeText(context, "No sound picker available on this device", Toast.LENGTH_SHORT)
                    .show()
            }
    }
    val oneShotDateLabel = remember(oneShotDate) {
        oneShotDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            ?: "Pick a date"
    }
    val repeatSubtitle = when {
        repeatDaily -> "Every day"
        else -> listOfNotNull("Once", oneShotDateLabel.takeIf { oneShotDate != null })
            .joinToString(" · ")
    }
    val soundSubtitle = if (!systemUri.isNullOrBlank()) {
        systemSounds.firstOrNull { it.uri == systemUri }?.title
            ?: pickedSound?.takeIf { it.uri == systemUri }?.title
            ?: "System sound"
    } else {
        tone.title
    }

    fun currentDraft(): AlarmItem {
        val date = if (repeatDaily) {
            null
        } else {
            oneShotDate ?: DayStartScheduler.defaultOneShotDate(hour, minute)
        }
        return initial.copy(
            name = name.ifBlank { "Alarm" },
            hour = hour,
            minute = minute,
            enabled = enabled,
            repeatDaily = repeatDaily,
            oneShotDate = date,
            tone = tone,
            systemSoundUri = systemUri,
            voiceEnabled = voiceEnabled,
            voiceStyle = voiceStyle,
            mode = mode,
            scheduleTasks = emptyList(),
            wakePhrase = phrase.ifBlank { "I AM AWAKE" }
        )
    }

    fun save() = onSave(currentDraft())

    if (showDatePicker) {
        val initialMillis = remember(oneShotDate) {
            val date = oneShotDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val todayUtc = remember {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ms = datePickerState.selectedDateMillis
                        if (ms != null) {
                            oneShotDate = Instant.ofEpochMilli(ms)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLabelEditor) {
        AlertDialog(
            onDismissRequest = { showLabelEditor = false },
            title = { Text(stringResource(R.string.pref_label)) },
            text = { DsTextField(name, { name = it }, stringResource(R.string.pref_label)) },
            confirmButton = {
                TextButton(onClick = { showLabelEditor = false }) { Text("OK") }
            }
        )
    }
    if (showPhraseEditor) {
        AlertDialog(
            onDismissRequest = { showPhraseEditor = false },
            title = { Text(stringResource(R.string.pref_phrase)) },
            text = { DsTextField(phrase, { phrase = it }, stringResource(R.string.pref_phrase)) },
            confirmButton = {
                TextButton(onClick = { showPhraseEditor = false }) { Text("OK") }
            }
        )
    }
    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text(stringResource(R.string.pref_wake_mode)) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WakeMode.entries.forEach { m ->
                        Chip(m.title, mode == m) {
                            mode = m
                            showModePicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModePicker = false }) { Text("Close") }
            }
        )
    }
    if (showVoiceStylePicker) {
        AlertDialog(
            onDismissRequest = { showVoiceStylePicker = false },
            title = { Text("Voice style") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceStyle.entries.forEach { style ->
                        Chip(style.title, voiceStyle == style) {
                            voiceStyle = style
                            showVoiceStylePicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceStylePicker = false }) { Text("Close") }
            }
        )
    }
    if (showSoundPicker) {
        AlertDialog(
            onDismissRequest = { showSoundPicker = false },
            title = { Text(stringResource(R.string.pref_sound)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Phone sounds", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        systemSounds.forEach { s: SystemSound ->
                            Chip(s.title, systemUri == s.uri) {
                                systemUri = s.uri
                                onPreviewTone(tone, s.uri)
                            }
                        }
                        // A custom pick from the full device library that isn't already
                        // in the curated shortlist above — shown so the current choice
                        // stays visible/selected instead of vanishing from the list.
                        pickedSound?.takeIf { picked -> systemSounds.none { it.uri == picked.uri } }
                            ?.let { picked ->
                                Chip(picked.title, systemUri == picked.uri) {
                                    systemUri = picked.uri
                                    onPreviewTone(tone, picked.uri)
                                }
                            }
                    }
                    SecondaryButton("Browse all sounds on this phone…") {
                        openRingtonePicker()
                    }
                    Text("Built-in", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlarmTone.entries.forEach { t ->
                            Chip(t.title, systemUri == null && tone == t) {
                                tone = t
                                systemUri = null
                                onPreviewTone(t, null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundPicker = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = ::save) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            TimeRoller(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                mod = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider()

            PrefRow(
                title = stringResource(R.string.pref_label),
                subtitle = name.ifBlank { "Alarm" },
                onClick = { showLabelEditor = true }
            )

            SectionHeader("When")
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), frosted = false) {
                PrefRow(
                    title = stringResource(R.string.pref_repeat),
                    subtitle = repeatSubtitle,
                    trailing = {
                        DsSwitch(repeatDaily) { on ->
                            repeatDaily = on
                            if (!on && oneShotDate == null) {
                                oneShotDate = DayStartScheduler.defaultOneShotDate(hour, minute)
                            }
                            if (on) oneShotDate = null
                        }
                    }
                )
                if (!repeatDaily) {
                    PrefRow(
                        title = "Date",
                        subtitle = oneShotDateLabel,
                        onClick = { showDatePicker = true }
                    )
                }
                PrefRow(
                    title = "Enabled",
                    subtitle = if (enabled) "On" else "Off",
                    trailing = { DsSwitch(enabled) { enabled = it } }
                )
            }

            SectionHeader("How it wakes you")
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), frosted = false) {
                PrefRow(
                    title = stringResource(R.string.pref_wake_mode),
                    subtitle = mode.title,
                    onClick = { showModePicker = true }
                )
                PrefRow(
                    title = stringResource(R.string.pref_phrase),
                    subtitle = phrase,
                    onClick = { showPhraseEditor = true }
                )
                PrefRow(
                    title = stringResource(R.string.pref_voice),
                    subtitle = if (voiceEnabled) voiceStyle.title else "Off",
                    trailing = {
                        DsSwitch(voiceEnabled) { voiceEnabled = it }
                    }
                )
                if (voiceEnabled) {
                    PrefRow(
                        title = "Voice style",
                        subtitle = voiceStyle.title,
                        onClick = { showVoiceStylePicker = true }
                    )
                }
            }

            SectionHeader("Sound")
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), frosted = false) {
                PrefRow(
                    title = stringResource(R.string.pref_sound),
                    subtitle = soundSubtitle,
                    onClick = { showSoundPicker = true }
                )
            }

            if (!isNew) {
                SectionHeader("Try it")
                GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), frosted = false) {
                    PrefRow(
                        title = stringResource(R.string.pref_test),
                        onClick = { onTest(currentDraft()) }
                    )
                }

                SectionHeader("Danger zone")
                GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), frosted = false) {
                    PrefRow(
                        title = stringResource(R.string.pref_delete),
                        onClick = { onDelete(initial.id) },
                        trailing = {
                            Text(
                                "Remove",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** [RingtoneManager.EXTRA_RINGTONE_PICKED_URI] via the type-safe API on 33+, deprecated below. */
private fun Intent.pickedRingtoneUri(): Uri? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
