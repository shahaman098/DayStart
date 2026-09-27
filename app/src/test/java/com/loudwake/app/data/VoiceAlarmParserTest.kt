package com.loudwake.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class VoiceAlarmParserTest {

    private val today = LocalDate.of(2026, 7, 20)
    private val morning = LocalDateTime.of(today, LocalTime.of(8, 0))

    @Test
    fun multiDateMultiTimeWithName() {
        val parsed = VoiceAlarmParser.parse(
            "Put an alarm on the 23rd of July at 5am and 10am, and the 24th of July at midnight for gym",
            today = today,
            now = morning
        )
        assertEquals(3, parsed.size)
        assertEquals("2026-07-23", parsed[0].oneShotDate)
        assertEquals(5, parsed[0].hour)
        assertEquals(0, parsed[0].minute)
        assertEquals("2026-07-23", parsed[1].oneShotDate)
        assertEquals(10, parsed[1].hour)
        assertEquals("2026-07-24", parsed[2].oneShotDate)
        assertEquals(0, parsed[2].hour)
        assertEquals("gym", parsed[2].name.lowercase())
    }

    @Test
    fun tomorrowMorning() {
        val parsed = VoiceAlarmParser.parse(
            "tomorrow at 7:30 am",
            today = today,
            now = morning
        )
        assertEquals(1, parsed.size)
        assertEquals("2026-07-21", parsed[0].oneShotDate)
        assertEquals(7, parsed[0].hour)
        assertEquals(30, parsed[0].minute)
    }

    @Test
    fun dailyAtSix() {
        val parsed = VoiceAlarmParser.parse("daily at 6 am", today = today, now = morning)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].repeatDaily)
        assertEquals(6, parsed[0].hour)
    }

    @Test
    fun emptyWhenNoTimes() {
        val parsed = VoiceAlarmParser.parse("wake me up sometime", today = today, now = morning)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun pastTimeTodayRollsToTomorrow() {
        val evening = LocalDateTime.of(today, LocalTime.of(21, 0))
        val parsed = VoiceAlarmParser.parse(
            "today at 5am",
            today = today,
            now = evening
        )
        assertEquals(1, parsed.size)
        assertEquals("2026-07-21", parsed[0].oneShotDate)
        assertEquals(5, parsed[0].hour)
    }

    @Test
    fun pastClockOnNamedDateRollsForward() {
        val evening = LocalDateTime.of(LocalDate.of(2026, 7, 23), LocalTime.of(21, 0))
        val parsed = VoiceAlarmParser.parse(
            "23rd of July at 5am",
            today = evening.toLocalDate(),
            now = evening
        )
        assertEquals(1, parsed.size)
        assertEquals("2026-07-24", parsed[0].oneShotDate)
        assertEquals(5, parsed[0].hour)
    }
}
