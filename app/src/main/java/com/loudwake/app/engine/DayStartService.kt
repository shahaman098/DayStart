package com.loudwake.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.loudwake.app.AlarmActivity
import com.loudwake.app.R
import com.loudwake.app.RelapseActivity
import com.loudwake.app.data.ChallengeKind
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.data.SessionPhase

class DayStartService : Service() {
    private var tonePlayer: TonePlayer? = null
    private var funnyVoice: FunnyVoice? = null
    private var vibrator: Vibrator? = null
    private var lastRelapse: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tonePlayer = TonePlayer(this)
        funnyVoice = FunnyVoice(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastRelapse = intent.getBooleanExtra(EXTRA_RELAPSE, false)
                if (!shouldPlayAudio(lastRelapse)) {
                    stopAssault()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundSafe(buildNotification(lastRelapse))
                startAssault(lastRelapse)
            }
            ACTION_PAUSE, ACTION_MUTE -> {
                // No audible media — must not hold a mediaPlayback FGS while silent.
                stopAssault()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            ACTION_STOP -> {
                stopAssault()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                // Sticky restart after kill — resume if session still needs sound
                resumeIfNeeded()
            }
        }
        val s = DayStartStore.session(this)
        val needsSticky = s.phase == SessionPhase.RINGING ||
            s.phase == SessionPhase.CHASE ||
            s.phase == SessionPhase.RELAPSE ||
            s.phase == SessionPhase.GATE
        return if (needsSticky) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAssault()
        funnyVoice?.release()
        funnyVoice = null
        super.onDestroy()
    }

    private fun resumeIfNeeded() {
        val s = DayStartStore.session(this)
        when (s.phase) {
            SessionPhase.RINGING, SessionPhase.CHASE -> {
                lastRelapse = false
                startForegroundSafe(buildNotification(false))
                startAssault(false)
            }
            SessionPhase.RELAPSE -> {
                lastRelapse = true
                startForegroundSafe(buildNotification(true))
                startAssault(true)
            }
            SessionPhase.GATE -> {
                val quiet =
                    s.gateFocused && s.gateQuietUntil > System.currentTimeMillis()
                if (!quiet) {
                    WakeEngine.onGateAbandoned(this)
                } else {
                    // Solving Wake Gate: stay silent without a mediaPlayback FGS.
                    stopAssault()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
    }

    private fun shouldPlayAudio(relapse: Boolean): Boolean {
        val s = DayStartStore.session(this)
        val quietSolving =
            s.phase == SessionPhase.GATE &&
                s.gateFocused &&
                s.gateQuietUntil > System.currentTimeMillis()
        val muted = s.safeSilence && s.nextRingAt > System.currentTimeMillis()
        if (quietSolving || muted) return false
        if (relapse || s.phase == SessionPhase.RELAPSE) return true
        return s.phase == SessionPhase.RINGING ||
            s.phase == SessionPhase.CHASE ||
            (s.phase == SessionPhase.GATE && !quietSolving)
    }

    private fun startAssault(relapse: Boolean) {
        val s = DayStartStore.session(this)
        if (!shouldPlayAudio(relapse)) {
            stopAssault()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        // Stale silence flag after mute window — force audible
        if (s.safeSilence) {
            DayStartStore.saveSession(this, s.copy(safeSilence = false))
        }
        val isRelapse = relapse || s.phase == SessionPhase.RELAPSE
        // Relapse Guard rings with the user's own chosen alarm tone/sound — using a fixed
        // joke tone here would undercut whatever loud tone they picked specifically to wake
        // them, right when Relapse Guard needs to be at its most reliable.
        val tone = s.tone
        val uri = s.systemSoundUri
        // Hard-max volume before (and during) assault — Oppo/ColorOS often ducks alarms
        tonePlayer?.pushVolumeMax()
        tonePlayer?.play(tone, looping = true, systemUri = uri)
        tonePlayer?.pushVolumeMax()
        if (s.voiceEnabled) {
            // Only nag about the wake phrase when the active mode actually has a phrase
            // challenge — otherwise a Math/Shake/Steps alarm confusingly "reminds" the
            // user of a phrase they'll never be asked to type.
            val usesPhrase = ChallengeKind.PHRASE in s.mode.challenges
            funnyVoice?.start(
                style = s.voiceStyle,
                wakePhrase = if (usesPhrase) s.wakePhrase else ""
            ) {
                when {
                    isRelapse -> WakeRoasts.randomRelapse()
                    s.phase == SessionPhase.CHASE -> WakeRoasts.randomChase()
                    else -> WakeRoasts.randomWake()
                }
            }
        } else {
            funnyVoice?.stop()
        }
        val pattern = longArrayOf(0, 600, 200, 600, 200, 900)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAssault() {
        funnyVoice?.stop()
        tonePlayer?.stop()
        vibrator?.cancel()
        AlarmWakeLock.release()
    }

    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(relapse: Boolean): Notification {
        val target = if (relapse) RelapseActivity::class.java else AlarmActivity::class.java
        val open = PendingIntent.getActivity(
            this,
            if (relapse) 2 else 1,
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (relapse) "Relapse Guard" else "WAKE UP — DayStart"
        val text = if (relapse) "Tap I’m Awake — prove it" else "Open Wake Gate — alarm is ringing"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSound(null) // sound comes from TonePlayer on STREAM_ALARM
            .setVibrate(longArrayOf(0, 800, 400, 800, 400, 1200))
            .setFullScreenIntent(open, true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        // Recreate — channel attrs are immutable after first create
        listOf("daystart_wake", "loud_alarms", "daystart_wake_v2").forEach {
            runCatching { nm.deleteNotificationChannel(it) }
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DayStart Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen wake alarms — must stay high"
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                null,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "daystart_alarm_v3"
        private const val NOTIF_ID = 42
        const val ACTION_START = "com.loudwake.app.DS_START"
        const val ACTION_PAUSE = "com.loudwake.app.DS_PAUSE"
        const val ACTION_MUTE = "com.loudwake.app.DS_MUTE"
        const val ACTION_STOP = "com.loudwake.app.DS_STOP"
        const val EXTRA_RELAPSE = "relapse"

        fun startRinging(context: Context, relapse: Boolean = false) {
            val s = DayStartStore.session(context)
            if (s.phase == SessionPhase.IDLE || s.phase == SessionPhase.DONE) return
            val now = System.currentTimeMillis()
            val quietSolving =
                s.phase == SessionPhase.GATE &&
                    s.gateFocused &&
                    s.gateQuietUntil > now
            // Honor Safe Silence only while the mute window is still active.
            // If nextRingAt already passed, do not stay stuck muted.
            val muted =
                s.safeSilence &&
                    s.nextRingAt > now
            if (quietSolving || muted) {
                Log.i("DayStartService", "startRinging skipped quiet=$quietSolving muted=$muted")
                return
            }
            val i = Intent(context, DayStartService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RELAPSE, relapse || s.phase == SessionPhase.RELAPSE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        private fun startServiceAction(context: Context, action: String) {
            val i = Intent(context, DayStartService::class.java).apply { this.action = action }
            runCatching { context.startService(i) }
        }

        fun pauseForGate(context: Context) = startServiceAction(context, ACTION_PAUSE)

        fun muteCurrent(context: Context) = startServiceAction(context, ACTION_MUTE)

        fun stopRinging(context: Context) = startServiceAction(context, ACTION_STOP)
    }
}
