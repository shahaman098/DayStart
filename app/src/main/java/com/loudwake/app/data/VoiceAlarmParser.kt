package com.loudwake.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.Locale

data class ParsedVoiceAlarm(
    val name: String,
    val hour: Int,
    val minute: Int,
    val oneShotDate: String?,
    val repeatDaily: Boolean
) {
    fun toAlarmItem(template: AlarmItem = AlarmItem()): AlarmItem =
        template.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { "Alarm" },
            hour = hour,
            minute = minute,
            enabled = true,
            repeatDaily = repeatDaily,
            oneShotDate = if (repeatDaily) null else oneShotDate
        )
}

/**
 * Rule-based English parser for spoken alarm scheduling.
 * Examples: "23rd of July at 5am and 10am", "tomorrow at 7", "daily at 6:30 am".
 */
object VoiceAlarmParser {

    private val months: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            put(m.name.lowercase(Locale.US), m)
            put(m.name.lowercase(Locale.US).take(3), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    private val datePattern = Regex(
        """(?:\b(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(""" +
            months.keys.joinToString("|") +
            """)(?:\s+(\d{4}))?\b)|(?:\b(""" +
            months.keys.joinToString("|") +
            """)\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s+(\d{4}))?\b)|(?:\b(today|tomorrow)\b)""",
        RegexOption.IGNORE_CASE
    )

    private val timePattern = Regex(
        """\b(?:midnight|noon|(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|am|pm)?|(?:at\s+)?(\d{1,2}):(\d{2}))\b""",
        RegexOption.IGNORE_CASE
    )

    private val namePattern = Regex(
        """\b(?:for|called|named)\s+(.+?)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now()
    ): List<ParsedVoiceAlarm> {
        val raw = transcript.trim()
        if (raw.isEmpty()) return emptyList()

        val text = normalize(raw)
        val repeatDaily = Regex("""\b(every\s+day|daily|each\s+day)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

        val globalName = namePattern.find(text)?.groupValues?.getOrNull(1)
            ?.replace(Regex("""[.!?]+$"""), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !looksLikeDateTimeJunk(it) }

        val dateMatches = datePattern.findAll(text).toList()
        if (dateMatches.isEmpty() && !repeatDaily) {
            // Times only → assume today (or tomorrow if past)
            val times = extractTimes(text)
            if (times.isEmpty()) return emptyList()
            return times.map { t ->
                val date = ensureSchedulable(today, t, now)
                ParsedVoiceAlarm(
                    name = globalName ?: "Alarm",
                    hour = t.hour,
                    minute = t.minute,
                    oneShotDate = date.toString(),
                    repeatDaily = false
                )
            }
        }

        if (repeatDaily && dateMatches.isEmpty()) {
            val times = extractTimes(text)
            if (times.isEmpty()) return emptyList()
            return times.map { t ->
                ParsedVoiceAlarm(
                    name = globalName ?: "Alarm",
                    hour = t.hour,
                    minute = t.minute,
                    oneShotDate = null,
                    repeatDaily = true
                )
            }
        }

        val results = mutableListOf<ParsedVoiceAlarm>()
        dateMatches.forEachIndexed { index, match ->
            val date = parseDateMatch(match, today) ?: return@forEachIndexed
            val segmentStart = match.range.first
            val segmentEnd = dateMatches.getOrNull(index + 1)?.range?.first ?: text.length
            val segment = text.substring(segmentStart, segmentEnd)
            val segmentName = namePattern.find(segment)?.groupValues?.getOrNull(1)
                ?.replace(Regex("""[.!?]+$"""), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() && !looksLikeDateTimeJunk(it) }
            val name = segmentName ?: globalName ?: "Alarm"
            val times = extractTimes(segment).ifEmpty {
                // "on July 23 for gym" without time — skip
                emptyList()
            }
            if (times.isEmpty()) return@forEachIndexed
            times.forEach { t ->
                val oneShot = when {
                    repeatDaily -> null
                    else -> ensureSchedulable(date, t, now).toString()
                }
                results += ParsedVoiceAlarm(
                    name = name,
                    hour = t.hour,
                    minute = t.minute,
                    oneShotDate = oneShot,
                    repeatDaily = repeatDaily
                )
            }
        }
        return results
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.US)
            .replace(Regex("""\s+"""), " ")
            .replace("o'clock", "")
            .replace("o clock", "")
            .trim()

    private fun looksLikeDateTimeJunk(s: String): Boolean =
        datePattern.containsMatchIn(s) || timePattern.containsMatchIn(s) ||
            s.matches(Regex("""^(am|pm|a\.m\.|p\.m\.)$""", RegexOption.IGNORE_CASE))

    private fun parseDateMatch(match: MatchResult, today: LocalDate): LocalDate? {
        val g = match.groupValues
        // Group layout from alternation:
        // 1 day, 2 month, 3 year | 4 month, 5 day, 6 year | 7 today/tomorrow
        when {
            g.getOrNull(7)?.isNotBlank() == true -> {
                return when (g[7].lowercase(Locale.US)) {
                    "today" -> today
                    "tomorrow" -> today.plusDays(1)
                    else -> null
                }
            }
            g.getOrNull(1)?.isNotBlank() == true && g.getOrNull(2)?.isNotBlank() == true -> {
                val day = g[1].toIntOrNull() ?: return null
                val month = months[g[2].lowercase(Locale.US)] ?: return null
                val year = g.getOrNull(3)?.toIntOrNull() ?: pickYear(today, month, day)
                return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
            g.getOrNull(4)?.isNotBlank() == true && g.getOrNull(5)?.isNotBlank() == true -> {
                val month = months[g[4].lowercase(Locale.US)] ?: return null
                val day = g[5].toIntOrNull() ?: return null
                val year = g.getOrNull(6)?.toIntOrNull() ?: pickYear(today, month, day)
                return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
        }
        return null
    }

    private fun pickYear(today: LocalDate, month: Month, day: Int): Int {
        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
            ?: return today.year
        return if (candidate.isBefore(today)) today.year + 1 else today.year
    }

    /**
     * Ensure date+time is still armable. Past clock times roll forward one day at a time
     * (e.g. “today at 5am” spoken at night → tomorrow 5am).
     */
    internal fun ensureSchedulable(
        date: LocalDate,
        time: LocalTime,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDate {
        var d = date
        val skew = now.plusSeconds(2)
        var guard = 0
        while (!LocalDateTime.of(d, time).isAfter(skew) && guard < 400) {
            d = d.plusDays(1)
            guard++
        }
        return d
    }

    private fun extractTimes(segment: String): List<LocalTime> {
        val out = mutableListOf<LocalTime>()
        // Prefer scanning with a simpler dedicated matcher to avoid double-capture
        val simple = Regex(
            """\b(midnight|noon|(\d{1,2})(?::(\d{2}))?\s*(a\.?\s*m\.?|p\.?\s*m\.?|am|pm)?)\b""",
            RegexOption.IGNORE_CASE
        )
        simple.findAll(segment).forEach { m ->
            val token = m.groupValues[1].lowercase(Locale.US).replace(" ", "")
            when {
                token.startsWith("midnight") -> out += LocalTime.MIDNIGHT
                token.startsWith("noon") -> out += LocalTime.NOON
                else -> {
                    val hourRaw = m.groupValues[2].toIntOrNull() ?: return@forEach
                    val minute = m.groupValues[3].toIntOrNull() ?: 0
                    val ampm = m.groupValues[4].lowercase(Locale.US)
                        .replace(".", "")
                        .replace(" ", "")
                    val hour = when {
                        ampm.startsWith("am") -> when (hourRaw) {
                            12 -> 0
                            else -> hourRaw
                        }
                        ampm.startsWith("pm") -> when (hourRaw) {
                            12 -> 12
                            else -> hourRaw + 12
                        }
                        hourRaw in 0..23 && m.groupValues[3].isNotBlank() -> hourRaw
                        hourRaw in 1..12 && ampm.isBlank() -> {
                            // Bare 1–12 without am/pm: treat as stated hour (morning-biased for alarms)
                            hourRaw
                        }
                        else -> hourRaw
                    }
                    if (hour in 0..23 && minute in 0..59) {
                        out += LocalTime.of(hour, minute)
                    }
                }
            }
        }
        return out.distinct()
    }
}
