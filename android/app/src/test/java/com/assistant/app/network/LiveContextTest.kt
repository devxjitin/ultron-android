package com.assistant.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveContextTest {
    @Test fun fractionIsClamped() {
        assertEquals(0f, LiveContext.fraction(-5), 0f)
        assertEquals(0.5f, LiveContext.fraction(LiveContext.LIMIT_TOKENS / 2), 0.001f)
        assertEquals(1f, LiveContext.fraction(LiveContext.LIMIT_TOKENS * 2), 0f)
    }

    @Test fun levelsChangeAtSeventyAndNinetyPercent() {
        assertEquals(LiveContext.Level.NORMAL, LiveContext.level(0.69f))
        assertEquals(LiveContext.Level.WARNING, LiveContext.level(0.7f))
        assertEquals(LiveContext.Level.CRITICAL, LiveContext.level(0.9f))
    }

    @Test fun labelShowsThousands() {
        assertEquals("41k / 65k", LiveContext.label(41_500))
    }
}
