package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSessionsTest {
    private fun turn(id: Long, session: String, user: String, assistant: String, at: Long) =
        StoredTurn(id, session, user, assistant, at, embedded = true)

    private val turns = listOf(
        turn(1, "a", "what is the weather", "Sunny.", 1_000),
        turn(2, "b", "set an alarm for six", "Done.", 5_000),
        turn(3, "a", "and tomorrow?", "Cloudy.", 2_000),
        turn(4, "b", "cancel it", "Cancelled.", 6_000)
    )

    @Test fun exchangesAreGroupedByTheirSessionNewestSessionFirst() {
        val sessions = groupBySession(turns)
        assertEquals(listOf("b", "a"), sessions.map { it.sessionId })
        assertEquals(listOf(3L, 1L).reversed(), sessions[1].turns.map { it.id })
        assertEquals(listOf(2L, 4L), sessions[0].turns.map { it.id })
    }

    @Test fun aSessionIsTitledByTheFirstThingTheUserSaid() {
        assertEquals("what is the weather", groupBySession(turns).first { it.sessionId == "a" }.title)
    }

    @Test fun searchKeepsSessionsContainingEveryWordInAnyOrder() {
        val sessions = groupBySession(turns)
        assertEquals(listOf("b"), filterSessions(sessions, "CANCEL alarm").map { it.sessionId })
        assertEquals(2, filterSessions(sessions, "  ").size)
        assertEquals(0, filterSessions(sessions, "pizza").size)
    }
}
