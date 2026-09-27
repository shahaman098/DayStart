package com.loudwake.app.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.loudwake.app.data.VoiceStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loud, looping funny wake lines on STREAM_ALARM while the assault runs.
 * Complements [TonePlayer] — never mutes or replaces the alarm tone.
 */
class FunnyVoice(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var running = false
    private var style: VoiceStyle = VoiceStyle.FUNNY
    private var wakePhrase: String = ""
    private var lineProvider: () -> String = { WakeRoasts.randomWake() }
    private val tick = AtomicInteger(0)

    private val speakLoop = object : Runnable {
        override fun run() {
            if (!running) return
            val n = tick.incrementAndGet()
            val phrase = wakePhrase.trim()
            val line = if (phrase.isNotEmpty() && n % 3 == 0) {
                "Wake phrase reminder: $phrase"
            } else {
                lineProvider()
            }
            speakNow(line)
            handler.postDelayed(this, 5_500L)
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts?.apply {
            language = Locale.US
            applyStyle(style)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }
        if (running) {
            handler.removeCallbacks(speakLoop)
            handler.post(speakLoop)
        }
    }

    fun start(
        style: VoiceStyle = VoiceStyle.FUNNY,
        wakePhrase: String = "",
        lines: () -> String
    ) {
        this.style = style
        this.wakePhrase = wakePhrase
        lineProvider = lines
        tick.set(0)
        running = true
        if (ready) {
            applyStyle(style)
            handler.removeCallbacks(speakLoop)
            handler.post(speakLoop)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(speakLoop)
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }

    private fun applyStyle(style: VoiceStyle) {
        tts?.apply {
            setPitch(style.pitch)
            setSpeechRate(style.speechRate)
        }
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        if (!ready) return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            UUID.randomUUID().toString()
        )
    }
}
