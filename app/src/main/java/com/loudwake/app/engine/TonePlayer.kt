package com.loudwake.app.engine

import android.app.NotificationManager
import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.loudwake.app.R
import com.loudwake.app.data.AlarmTone

/**
 * Alarm playback that always overrides device volume to maximum while active.
 * Uses STREAM_ALARM + LoudnessEnhancer + dual MediaPlayer stack + volume observer.
 */
class TonePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var playerB: MediaPlayer? = null
    private var booster: LoudnessEnhancer? = null
    private var boosterB: LoudnessEnhancer? = null
    private var ringtone: Ringtone? = null
    private var beeper: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var active = false
    private var observingVolume = false
    private val handler = Handler(Looper.getMainLooper())
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var savedVolumes: Map<Int, Int>? = null
    private var savedRingerMode: Int? = null

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (active) forceMaxVolume()
        }
    }

    private val volumeGuard = object : Runnable {
        override fun run() {
            if (!active) return
            forceMaxVolume()
            try {
                player?.setVolume(1f, 1f)
                playerB?.setVolume(1f, 1f)
                booster?.apply {
                    setTargetGain(GAIN_MB)
                    enabled = true
                }
                boosterB?.apply {
                    setTargetGain(GAIN_MB)
                    enabled = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone?.volume = 1f
                }
                // Restart if OEM killed playback
                if (player?.isPlaying == false) {
                    runCatching { player?.start() }
                }
                if (playerB?.isPlaying == false) {
                    runCatching { playerB?.start() }
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, GUARD_MS)
        }
    }

    private val ringtoneLoop = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                val rt = ringtone ?: return
                if (!rt.isPlaying) rt.play()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 3_000L)
        }
    }

    private val beepLoop = object : Runnable {
        override fun run() {
            if (!active || beeper == null) return
            try {
                beeper?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 2000L)
        }
    }

    /** Always override streams to absolute max — ignore current user volume. */
    fun pushVolumeMax() = forceMaxVolume()

    private fun forceMaxVolume() {
        try {
            if (savedVolumes == null) {
                savedVolumes = VOLUME_STREAMS.associateWith { stream ->
                    runCatching { am.getStreamVolume(stream) }.getOrDefault(0)
                }
                savedRingerMode = runCatching { am.ringerMode }.getOrNull()
            }

            runCatching { am.ringerMode = AudioManager.RINGER_MODE_NORMAL }

            for (stream in VOLUME_STREAMS) {
                val max = am.getStreamMaxVolume(stream)
                if (max <= 0) continue
                // Always rewrite — Oppo/ColorOS often lets volume keys duck mid-ring
                runCatching { am.setStreamVolume(stream, max, 0) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    }
                }
            }

            runCatching {
                @Suppress("DEPRECATION")
                if (!am.isSpeakerphoneOn) am.isSpeakerphoneOn = true
            }

            // Prefer MODE_NORMAL; never leave in communication mode that ducks alarms
            runCatching {
                if (am.mode != AudioManager.MODE_NORMAL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            }

            bypassDndIfAllowed()
        } catch (t: Throwable) {
            Log.w(TAG, "forceMaxVolume failed", t)
        }
    }

    private fun startVolumeOverride() {
        forceMaxVolume()
        if (!observingVolume) {
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    volumeObserver
                )
                observingVolume = true
            }
        }
        handler.removeCallbacks(volumeGuard)
        handler.post(volumeGuard)
    }

    private fun stopVolumeOverride(restore: Boolean) {
        handler.removeCallbacks(volumeGuard)
        if (observingVolume) {
            runCatching {
                context.contentResolver.unregisterContentObserver(volumeObserver)
            }
            observingVolume = false
        }
        if (restore) restoreVolume()
    }

    private fun bypassDndIfAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (!nm.isNotificationPolicyAccessGranted) return
            // Allow alarms through total silence when we have policy access
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreVolume() {
        try {
            savedVolumes?.forEach { (stream, vol) ->
                runCatching { am.setStreamVolume(stream, vol, 0) }
            }
            savedRingerMode?.let { mode ->
                runCatching { am.ringerMode = mode }
            }
            runCatching {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        } catch (_: Exception) {
        }
        savedVolumes = null
        savedRingerMode = null
    }

    fun play(tone: AlarmTone, looping: Boolean = true, systemUri: String? = null) {
        stop(restore = false)
        active = true
        startVolumeOverride()
        requestAlarmFocus()
        if (!systemUri.isNullOrBlank()) {
            if (playUri(Uri.parse(systemUri), looping)) return
        }
        if (playRaw(tone, looping)) return
        val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (playUri(def, looping)) return
        fallbackBeep()
    }

    fun preview(tone: AlarmTone, ms: Int = 2000, systemUri: String? = null) {
        stop()
        active = true
        startVolumeOverride()
        requestAlarmFocus()
        val ok = if (!systemUri.isNullOrBlank()) {
            playUri(Uri.parse(systemUri), looping = false)
        } else {
            playRaw(tone, looping = false)
        }
        if (ok) handler.postDelayed({ stop() }, ms.toLong())
    }

    fun stop(restore: Boolean = true) {
        active = false
        handler.removeCallbacks(ringtoneLoop)
        handler.removeCallbacks(beepLoop)
        abandonFocus()
        releasePlayers()
        stopVolumeOverride(restore)
    }

    private fun releasePlayers() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        try {
            playerB?.stop()
        } catch (_: Exception) {
        }
        playerB?.release()
        playerB = null
        releaseBooster(booster)
        booster = null
        releaseBooster(boosterB)
        boosterB = null
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        beeper?.release()
        beeper = null
    }

    private fun releaseBooster(b: LoudnessEnhancer?) {
        try {
            b?.enabled = false
            b?.release()
        } catch (_: Exception) {
        }
    }

    private fun makeBooster(sessionId: Int): LoudnessEnhancer? =
        runCatching {
            LoudnessEnhancer(sessionId).apply {
                setTargetGain(GAIN_MB)
                enabled = true
            }
        }.onFailure {
            Log.w(TAG, "LoudnessEnhancer unavailable", it)
        }.getOrNull()

    private fun playRaw(tone: AlarmTone, looping: Boolean): Boolean {
        return try {
            val res = toneRes(tone)
            player = buildPlayer(resId = res, looping = looping).also {
                booster = makeBooster(it.audioSessionId)
                it.start()
            }
            // Second stacked player — denser / louder on many OEMs
            playerB = buildPlayer(resId = res, looping = looping).also {
                boosterB = makeBooster(it.audioSessionId)
                handler.postDelayed({
                    if (active) runCatching { it.start() }
                }, 40L)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "playRaw failed ${tone.name}", t)
            false
        }
    }

    private fun buildPlayer(resId: Int, looping: Boolean): MediaPlayer {
        val mp = MediaPlayer()
        mp.setAudioAttributes(alarmAttrs())
        val afd = context.resources.openRawResourceFd(resId)
        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        mp.isLooping = looping
        mp.prepare()
        mp.setVolume(1f, 1f)
        return mp
    }

    private fun playUri(uri: Uri, looping: Boolean): Boolean {
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttrs())
            mp.setDataSource(context, uri)
            mp.isLooping = looping
            mp.prepare()
            booster = makeBooster(mp.audioSessionId)
            mp.setVolume(1f, 1f)
            mp.start()
            player = mp
            true
        } catch (t: Throwable) {
            Log.w(TAG, "playUri MediaPlayer failed $uri", t)
            try {
                val rt = RingtoneManager.getRingtone(context, uri) ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.audioAttributes = alarmAttrs()
                    rt.volume = 1f
                }
                rt.play()
                ringtone = rt
                if (looping) handler.postDelayed(ringtoneLoop, 3_000L)
                true
            } catch (t2: Throwable) {
                Log.e(TAG, "playUri Ringtone failed", t2)
                false
            }
        }
    }

    private fun requestAlarmFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(alarmAttrs())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { change ->
                        if (!active) return@setOnAudioFocusChangeListener
                        forceMaxVolume()
                        player?.setVolume(1f, 1f)
                        playerB?.setVolume(1f, 1f)
                        if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            // Immediately reclaim
                            runCatching { am.requestAudioFocus(focusRequest!!) }
                            runCatching { player?.start() }
                            runCatching { playerB?.start() }
                        }
                    }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        focusRequest = null
    }

    private fun alarmAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

    private fun fallbackBeep() {
        beeper = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
            it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
        }
        handler.post(beepLoop)
    }

    private fun toneRes(tone: AlarmTone): Int = when (tone) {
        AlarmTone.SHOCKWAVE -> R.raw.shockwave
        AlarmTone.AIR_HORN -> R.raw.air_horn
        AlarmTone.KLAXON -> R.raw.klaxon
        AlarmTone.SIREN -> R.raw.siren
        AlarmTone.PULSE_STORM -> R.raw.pulse_storm
        AlarmTone.BUZZSAW -> R.raw.buzzsaw
        AlarmTone.RADAR -> R.raw.radar
        AlarmTone.REVEILLE -> R.raw.reveille
        AlarmTone.BOING_WAKE -> R.raw.boing_wake
        AlarmTone.ROOSTER_CHAOS -> R.raw.rooster_chaos
        AlarmTone.WAKE_WHISTLE -> R.raw.wake_whistle
        AlarmTone.COMEDY_HONK -> R.raw.comedy_honk
        AlarmTone.CLOWN_HORN -> R.raw.clown_horn
        AlarmTone.CLOWN_HONKS -> R.raw.clown_honks
        AlarmTone.SAD_TROMBONE -> R.raw.sad_trombone
        AlarmTone.TROMBONE_WAH -> R.raw.trombone_wah
        AlarmTone.TOY_WHISTLE -> R.raw.toy_whistle
        AlarmTone.FALLING_WHISTLE -> R.raw.falling_whistle
        AlarmTone.CARTOON_BOING -> R.raw.cartoon_boing
        AlarmTone.FUNNY_GIGGLE -> R.raw.funny_giggle
        AlarmTone.CREATURE_LAUGH -> R.raw.creature_laugh
        AlarmTone.GAME_SLIDE -> R.raw.game_slide
        AlarmTone.CLOWN_TOOT -> R.raw.clown_toot
        AlarmTone.FAIL_BUZZER -> R.raw.fail_buzzer
        AlarmTone.PARTY_HORN -> R.raw.party_horn
        AlarmTone.FUNNY_MELODY -> R.raw.funny_melody
        AlarmTone.ROOSTER_MORNING -> R.raw.rooster_morning
        AlarmTone.CLASSIC_ALARM -> R.raw.classic_alarm
        AlarmTone.RETRO_ALARM -> R.raw.retro_alarm
        AlarmTone.WINNER_ALARM -> R.raw.winner_alarm
        AlarmTone.FACILITY_ALARM -> R.raw.mixkit_facility_alarm_sound
        AlarmTone.FACILITY_ALARM_SHORT -> R.raw.mixkit_facility_alarm
        AlarmTone.RETRO_EMERGENCY -> R.raw.mixkit_retro_game_emergency_alarm
        AlarmTone.DIGITAL_BUZZER -> R.raw.mixkit_digital_alarm_buzzer
        AlarmTone.ALERT_ALARM -> R.raw.mixkit_alert_alarm
        AlarmTone.VINTAGE_WARNING -> R.raw.mixkit_vintage_warning_alarm
        AlarmTone.EMERGENCY_ALERT -> R.raw.mixkit_emergency_alert_alarm
        AlarmTone.WARNING_BUZZER -> R.raw.mixkit_warning_alarm_buzzer
        AlarmTone.CITY_SIREN -> R.raw.mixkit_city_alert_siren_loop
        AlarmTone.POLICE_SIREN -> R.raw.mixkit_police_siren_us
        AlarmTone.AMBULANCE_SIREN -> R.raw.mixkit_ambulance_siren_us
        AlarmTone.AMBULANCE_UK -> R.raw.mixkit_ambulance_siren_uk
        AlarmTone.AMBULANCE_PASS -> R.raw.mixkit_ambulance_siren_passing_by
        AlarmTone.SCI_FI_SIREN -> R.raw.mixkit_sci_fi_ship_siren_alert
        AlarmTone.BURGLAR_SIREN -> R.raw.mixkit_residential_burglar_siren
        AlarmTone.FIRE_TRUCK_SIREN -> R.raw.mixkit_europe_fire_truck_siren
        AlarmTone.MANUAL_FIRE_SIREN -> R.raw.mixkit_manual_fire_siren
        AlarmTone.CLASSIC_ALARM_CLOCK -> R.raw.mixkit_classic_alarm_clock
        AlarmTone.CLASSIC_CAR_HORN -> R.raw.mixkit_classic_car_horn
        AlarmTone.CAR_DOUBLE_HORN -> R.raw.mixkit_car_double_horn
        AlarmTone.WARFARE_HORN -> R.raw.mixkit_warfare_horn
    }

    companion object {
        private const val TAG = "TonePlayer"
        /** +35 dB LoudnessEnhancer target (millibels) — raised from +25 dB, reported too quiet. */
        private const val GAIN_MB = 3500
        private const val GUARD_MS = 150L
        private val VOLUME_STREAMS = intArrayOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL
        )
    }
}
