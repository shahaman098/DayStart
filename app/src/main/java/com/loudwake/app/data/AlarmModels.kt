package com.loudwake.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Alarm",
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** When true, re-arms for the next day after each fire. */
    val repeatDaily: Boolean = true,
    /**
     * Local calendar day for one-shot alarms (`yyyy-MM-dd`).
     * Ignored when [repeatDaily] is true.
     */
    val oneShotDate: String? = null,
    // SIREN measures loudest of every bundled tone (integrated loudness, ffmpeg ebur128) and
    // its pitch sweep is the same wake/alert pattern proven over a century of civil-defense,
    // fire, and emergency-vehicle use — harder to sleep through or tune out than a static tone.
    val tone: AlarmTone = AlarmTone.SIREN,
    /** If set, play this system/content URI instead of bundled [tone]. */
    val systemSoundUri: String? = null,
    /** Spoken roast/cue lines via TTS; does not replace the loud alarm tone. */
    val voiceEnabled: Boolean = true,
    val voiceStyle: VoiceStyle = VoiceStyle.FUNNY,
    val mode: WakeMode = WakeMode.MATH,
    val scheduleTasks: List<String> = listOf(
        "Get out of bed",
        "Drink water",
        "Open the blinds"
    ),
    val wakePhrase: String = "I AM AWAKE"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("repeatDaily", repeatDaily)
        put("oneShotDate", oneShotDate ?: "")
        put("tone", tone.name)
        put("systemSoundUri", systemSoundUri ?: "")
        put("voiceEnabled", voiceEnabled)
        put("voiceStyle", voiceStyle.name)
        put("mode", mode.name)
        put("scheduleTasks", JSONArray(scheduleTasks))
        put("wakePhrase", wakePhrase)
    }

    fun soundLabel(): String =
        if (!systemSoundUri.isNullOrBlank()) "System sound" else tone.title

    fun oneShotLocalDate(): LocalDate? =
        oneShotDate?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun dateLabel(): String? =
        oneShotLocalDate()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun repeatLabel(): String = when {
        repeatDaily -> "Daily"
        else -> listOfNotNull("Once", dateLabel()).joinToString(" · ")
    }

    companion object {
        fun fromJson(o: JSONObject): AlarmItem = AlarmItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Alarm"),
            hour = o.optInt("hour", 7),
            minute = o.optInt("minute", 0),
            enabled = o.optBoolean("enabled", true),
            repeatDaily = o.optBoolean("repeatDaily", true),
            oneShotDate = o.optString("oneShotDate").ifBlank { null },
            tone = AlarmTone.fromName(o.optString("tone")),
            systemSoundUri = o.optString("systemSoundUri").ifBlank { null },
            // Default on — matches prior always-on FunnyVoice behavior
            voiceEnabled = o.optBoolean("voiceEnabled", true),
            voiceStyle = VoiceStyle.fromName(o.optString("voiceStyle")),
            mode = WakeMode.fromName(o.optString("mode")),
            // burstCount was removed; ignore leftover prefs harmlessly
            scheduleTasks = buildList {
                val arr = o.optJSONArray("scheduleTasks")
                if (arr != null) {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
                if (isEmpty()) {
                    add("Get out of bed")
                    add("Drink water")
                    add("Open the blinds")
                }
            },
            wakePhrase = o.optString("wakePhrase", "I AM AWAKE")
        )
    }
}

enum class SessionPhase {
    IDLE,
    /** Alarm is ringing — open Wake Gate (or Safe Silence / Stop → Chase). */
    RINGING,
    GATE,
    RELAPSE_WAIT,
    RELAPSE,
    CHASE,
    DONE;

    companion object {
        fun fromName(raw: String?): SessionPhase {
            val name = raw?.trim().orEmpty()
            if (name == "BURSTING") return RINGING // migrate pre-burst-removal sessions
            return entries.firstOrNull { it.name == name } ?: IDLE
        }
    }
}

data class WakeSession(
    val alarmId: String = "",
    val phase: SessionPhase = SessionPhase.IDLE,
    val chaseRemaining: Int = 0,
    // SIREN measures loudest of every bundled tone (integrated loudness, ffmpeg ebur128) and
    // its pitch sweep is the same wake/alert pattern proven over a century of civil-defense,
    // fire, and emergency-vehicle use — harder to sleep through or tune out than a static tone.
    val tone: AlarmTone = AlarmTone.SIREN,
    val systemSoundUri: String? = null,
    val voiceEnabled: Boolean = true,
    val voiceStyle: VoiceStyle = VoiceStyle.FUNNY,
    val mode: WakeMode = WakeMode.MATH,
    val scheduleTasks: List<String> = emptyList(),
    val wakePhrase: String = "I AM AWAKE",
    val challengeIndex: Int = 0,
    val relapseDeadlineAt: Long = 0L,
    val safeSilence: Boolean = false,
    val safeSilenceUsed: Int = 0,
    val gateFocused: Boolean = false,
    /** Quiet Wake Gate only until this time; stale focus cannot mute forever. */
    val gateQuietUntil: Long = 0L,
    /** Next scheduled re-ring / chase pressure; used to recover if AlarmManager drops it. */
    val nextRingAt: Long = 0L,
    /** Full chase rounds completed (each round = [WakeEngine.CHASE_COUNT] rings). Caps endless chase. */
    val chaseCycles: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("alarmId", alarmId)
        put("phase", phase.name)
        put("chaseRemaining", chaseRemaining)
        put("tone", tone.name)
        put("systemSoundUri", systemSoundUri ?: "")
        put("voiceEnabled", voiceEnabled)
        put("voiceStyle", voiceStyle.name)
        put("mode", mode.name)
        put("scheduleTasks", JSONArray(scheduleTasks))
        put("wakePhrase", wakePhrase)
        put("challengeIndex", challengeIndex)
        put("relapseDeadlineAt", relapseDeadlineAt)
        put("safeSilence", safeSilence)
        put("safeSilenceUsed", safeSilenceUsed)
        put("gateFocused", gateFocused)
        put("gateQuietUntil", gateQuietUntil)
        put("nextRingAt", nextRingAt)
        put("chaseCycles", chaseCycles)
    }

    companion object {
        fun fromJson(o: JSONObject?): WakeSession {
            if (o == null) return WakeSession()
            val tasks = buildList {
                val arr = o.optJSONArray("scheduleTasks")
                if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
            }
            // Prefer nextRingAt; fall back to legacy nextBurstAt
            val nextRing = o.optLong("nextRingAt").takeIf { it > 0L }
                ?: o.optLong("nextBurstAt")
            return WakeSession(
                alarmId = o.optString("alarmId"),
                phase = SessionPhase.fromName(o.optString("phase", SessionPhase.IDLE.name)),
                chaseRemaining = o.optInt("chaseRemaining"),
                tone = AlarmTone.fromName(o.optString("tone")),
                systemSoundUri = o.optString("systemSoundUri").ifBlank { null },
                voiceEnabled = o.optBoolean("voiceEnabled", true),
                voiceStyle = VoiceStyle.fromName(o.optString("voiceStyle")),
                mode = WakeMode.fromName(o.optString("mode")),
                scheduleTasks = tasks,
                wakePhrase = o.optString("wakePhrase", "I AM AWAKE"),
                challengeIndex = o.optInt("challengeIndex"),
                relapseDeadlineAt = o.optLong("relapseDeadlineAt"),
                safeSilence = o.optBoolean("safeSilence"),
                safeSilenceUsed = o.optInt("safeSilenceUsed"),
                gateFocused = o.optBoolean("gateFocused"),
                gateQuietUntil = o.optLong("gateQuietUntil"),
                nextRingAt = nextRing,
                chaseCycles = o.optInt("chaseCycles")
            )
        }
    }
}
