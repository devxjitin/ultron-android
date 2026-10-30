package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    private val now = 10_000_000L

    @Test fun turnsWithinThirtyMinutesAreKeptAndOlderOnesDropped() {
        val recent = ConversationTurn("a", "b", now - ConversationStore.HISTORY_TTL_MS + 1)
        val stale = ConversationTurn("c", "d", now - ConversationStore.HISTORY_TTL_MS - 1)
        assertEquals(listOf(recent), ConversationStore.freshTurns(listOf(stale, recent), now))
    }

    @Test fun turnsStampedInTheFutureAreDropped() {
        assertTrue(ConversationStore.freshTurns(listOf(ConversationTurn("a", "b", now + 5_000)), now).isEmpty())
    }
}
