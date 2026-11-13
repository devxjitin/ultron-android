package com.assistant.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveContextTest {
    @Test fun fractionIsClamped() {
        assertEquals(0f, LiveContext.fraction(-5), 0f)
        assertEquals(0.5f, LiveContext.fraction(LiveContext.LIMIT_TOKENS / 2), 0.001f)
        assertEquals(1f, LiveContext.fraction(LiveContext.LIMIT_TOKENS * 2), 0f)
    }

    @Test fun levelsChangeAtFortyTwoAndFiftyFourPercent() {
        assertEquals(LiveContext.Level.NORMAL, LiveContext.level(0.41f))
        assertEquals(LiveContext.Level.WARNING, LiveContext.level(0.42f))
        assertEquals(LiveContext.Level.CRITICAL, LiveContext.level(0.54f))
    }

    @Test fun compactionStartsAtSixtyPercent() {
        assertEquals(39_321, LiveContext.COMPRESS_AT_TOKENS)
    }

    @Test fun labelShowsThousands() {
        assertEquals("41k / 65k", LiveContext.label(41_500))
    }
}
