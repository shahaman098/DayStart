package com.loudwake.app.engine

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

object VoiceAlarmSpeech {
    fun createListenIntent(prompt: String = "Say dates and times for your alarms"): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    fun transcriptFromResult(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return matches?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
