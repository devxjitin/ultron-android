package com.assistant.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledPromptsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test fun `a one-time prompt has no next run`() {
        assertNull(ScheduledPrompts.nextRun(at("2026-11-07T21:00"), PromptRepeat.ONCE, at("2026-11-07T21:00:05"), zone))
    }

    @Test fun `daily keeps the same clock time`() {
        assertEquals(at("2026-11-08T21:00"), ScheduledPrompts.nextRun(at("2026-11-07T21:00"), PromptRepeat.DAILY, at("2026-11-07T21:01"), zone))
    }

    @Test fun `a daily prompt missed for days catches up to the next future time`() {
        assertEquals(at("2026-11-11T21:00"), ScheduledPrompts.nextRun(at("2026-11-07T21:00"), PromptRepeat.DAILY, at("2026-11-11T08:00"), zone))
    }

    @Test fun `weekdays skip the weekend`() {
        // 2026-11-06 is a Friday.
        assertEquals(at("2026-11-09T07:00"), ScheduledPrompts.nextRun(at("2026-11-06T07:00"), PromptRepeat.WEEKDAYS, at("2026-11-06T07:01"), zone))
    }

    @Test fun `weekly moves seven days`() {
        assertEquals(at("2026-11-14T10:00"), ScheduledPrompts.nextRun(at("2026-11-07T10:00"), PromptRepeat.WEEKLY, at("2026-11-07T10:01"), zone))
    }

    @Test fun `a time already past today means tomorrow`() {
        val now = at("2026-11-07T22:00")
        assertEquals(at("2026-11-08T21:00"), ScheduledPrompts.firstRun(LocalTime.of(21, 0), null, PromptRepeat.ONCE, now, zone))
        assertEquals(at("2026-11-07T23:00"), ScheduledPrompts.firstRun(LocalTime.of(23, 0), null, PromptRepeat.ONCE, now, zone))
    }

    @Test fun `a weekday prompt picked for Saturday starts on Monday`() {
        val now = at("2026-11-06T10:00")
        assertEquals(
            at("2026-11-09T09:00"),
            ScheduledPrompts.firstRun(LocalTime.of(9, 0), LocalDate.parse("2026-11-07"), PromptRepeat.WEEKDAYS, now, zone)
        )
    }

    @Test fun `saved prompts read back the same and bad rows are skipped`() {
        val items = listOf(
            ScheduledPrompt(1, "Send Sonia \"Hello\" on WhatsApp", at("2026-11-07T21:00"), PromptRepeat.DAILY),
            ScheduledPrompt(2, "Silent", at("2026-11-08T07:00"), PromptRepeat.ONCE, enabled = false, lastRunAt = 5, lastResult = "Done", lastOk = false)
        )
        assertEquals(items, ScheduledPrompts.fromJson(ScheduledPrompts.toJson(items)))
        assertEquals(1, ScheduledPrompts.fromJson("""[{"id":3,"prompt":"x","at":1},{"bad":true}]""").size)
    }

    @Test fun `unknown repeat values mean once`() {
        assertEquals(PromptRepeat.ONCE, PromptRepeat.from("hourly"))
        assertEquals(PromptRepeat.WEEKDAYS, PromptRepeat.from(" Weekdays "))
    }
}
