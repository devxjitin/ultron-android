package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun time(minutes: Int?, atTime: String?, date: String?, now: String) =
        AutomationEngine.reminderTime(minutes, atTime, date, millis(now), zone)

    @Test
    fun minutesCountFromNow() {
        assertEquals(millis("2026-11-05T10:30:00"), time(30, null, null, "2026-11-05T10:00:00"))
    }

    @Test
    fun aClockTimeLaterTodayIsToday() {
        assertEquals(millis("2026-11-05T17:00:00"), time(null, "17:00", null, "2026-11-05T10:00:00"))
    }

    @Test
    fun aClockTimeAlreadyPassedMeansTomorrow() {
        assertEquals(millis("2026-11-06T09:00:00"), time(null, "09:00", null, "2026-11-05T22:00:00"))
    }

    @Test
    fun aDateAndTimeAreUsedAsGiven() {
        assertEquals(millis("2026-11-20T08:15:00"), time(null, "08:15", "2026-11-20", "2026-11-05T10:00:00"))
    }

    @Test
    fun aDateInThePastIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { time(null, "08:15", "2026-11-01", "2026-11-05T10:00:00") }
    }

    @Test
    fun badInputIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { time(0, null, null, "2026-11-05T10:00:00") }
        assertThrows(IllegalArgumentException::class.java) { time(null, "25:99", null, "2026-11-05T10:00:00") }
        assertThrows(IllegalArgumentException::class.java) { time(null, "09:00", "soon", "2026-11-05T10:00:00") }
        assertThrows(IllegalArgumentException::class.java) { time(null, null, null, "2026-11-05T10:00:00") }
        assertThrows(IllegalArgumentException::class.java) { time(null, "09:00", "2028-01-01", "2026-11-05T10:00:00") }
    }

    @Test
    fun repeatWords() {
        assertEquals(1_440, AutomationEngine.repeatMinutes("daily"))
        assertEquals(10_080, AutomationEngine.repeatMinutes("Weekly"))
        assertEquals(0, AutomationEngine.repeatMinutes(null))
        assertEquals(0, AutomationEngine.repeatMinutes("sometimes"))
    }
}
