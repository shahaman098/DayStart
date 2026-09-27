package com.loudwake.app.data

/**
 * TTS roast delivery while the loud alarm tone keeps playing.
 * Complements [AlarmTone] — never replaces it.
 */
enum class VoiceStyle(
    val title: String,
    val pitch: Float,
    val speechRate: Float
) {
    FUNNY("Funny", 1.15f, 1.05f),
    CALM("Calm", 1.0f, 0.92f),
    DRILL("Drill", 0.85f, 1.18f);

    companion object {
        fun fromName(raw: String?): VoiceStyle =
            entries.firstOrNull { it.name == raw } ?: FUNNY
    }
}
