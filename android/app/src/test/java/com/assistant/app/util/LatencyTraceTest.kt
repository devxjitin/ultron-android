package com.assistant.app.util

import org.junit.Assert.*
import org.junit.Test

class LatencyTraceTest {
    private var time = 1000L
    private val events = mutableListOf<Map<String, Any>>()
    private fun session(id: String = "session-1", enabled: Boolean = true) =
        LatencySession(id, { time }, { events.add(it) }, enabled)
    private fun summaries() = events.filter { it["event"] == "turn_summary" }

    @Test fun `general turn finishes only after playback and server completion`() {
        val session = session()
        val turn = session.turn()!!
        turn.speech(1000, "rms")
        assertTrue(turn.audioReceived(1500))
        assertFalse(turn.audioReceived(1510))
        turn.audioWritten(1600, 120)
        session.complete()
        assertTrue(summaries().isEmpty())
        turn.playback(1620, 0)
        turn.playback(1630, 0)
        session.playbackIdle()
        val result = summaries().single()
        assertEquals("completed", result["outcome"])
        assertEquals(false, result["knowledge"])
        assertEquals(620L, result["speech_to_playback_ms"])
        assertEquals(120L, result["audio_to_playback_ms"])
    }

    @Test fun `tools share a turn and have distinct local IDs`() {
        val session = session()
        val turn = session.turn()!!
        val first = turn.tool()
        val second = turn.tool()
        second.event("embedding_end", mapOf("model" to "model-b"))
        first.event("embedding_end", mapOf("model" to "model-a"))
        val endings = events.filter { it["event"] == "embedding_end" }
        assertEquals(listOf(2, 1), endings.map { it["tool"] })
        assertEquals(listOf(1, 1), endings.map { it["turn"] })
        assertTrue(endings.all { it["session"] == "session-1" })
    }

    @Test fun `interruption prevents a late probe from contaminating the next turn`() {
        val session = session()
        val old = session.turn()!!
        old.audioReceived(1000)
        session.cancel("interrupted")
        val next = session.turn()!!
        old.playback(1100, 0)
        assertEquals(2, next.id)
        assertEquals("interrupted", summaries().single()["outcome"])
        assertFalse(events.any { it["event"] == "first_playback_progress" })
    }

    @Test fun `completed server turn retains its playback identity while next turn starts`() {
        val session = session()
        val old = session.turn()!!
        old.audioReceived(1100)
        session.complete()
        val next = session.turn()!!
        old.playback(1200, 0)
        old.drained()
        assertEquals(1, summaries().single()["turn"])
        assertEquals(2, next.id)
        assertFalse(next.finished)
    }

    @Test fun `reconnection creates distinct session identity and excludes failure`() {
        val first = session("connection-1")
        first.turn()!!.audioReceived(1000)
        first.cancel("disconnected")
        val second = session("connection-2")
        second.turn()!!.complete()
        assertEquals(listOf("connection-1", "connection-2"), summaries().map { it["session"] })
        assertEquals(listOf("disconnected", "no_audio"), summaries().map { it["outcome"] })
    }

    @Test fun `retrieval failure is not counted as successful even if model speaks`() {
        val session = session()
        val turn = session.turn()!!
        turn.tool().failure()
        turn.audioReceived(1200)
        turn.playback(1300, 0)
        session.complete()
        session.playbackIdle()
        assertEquals("failed", summaries().single()["outcome"])
    }

    @Test fun `transcript timing cannot replace an acoustic estimate`() {
        val turn = session().turn()!!
        turn.speech(1000, "rms")
        turn.speech(1400, "transcript_arrival")
        turn.audioReceived(1500)
        turn.playback(1600, 0)
        turn.complete()
        turn.drained()
        assertEquals("rms", summaries().single()["speech_source"])
        assertEquals(600L, summaries().single()["speech_to_playback_ms"])
    }

    @Test fun `disabled telemetry and failing sinks do not affect conversation`() {
        assertNull(session(enabled = false).turn())
        assertTrue(events.isEmpty())
        val broken = LatencySession("broken", { time }, { error("sink unavailable") })
        broken.turn()!!.complete()
    }

    @Test fun `interruption after server completion but before drain excludes success`() {
        val session = session()
        val turn = session.turn()!!
        turn.audioReceived(1100)
        turn.playback(1200, 0)
        session.complete()
        assertTrue(summaries().isEmpty())
        session.cancel("interrupted")
        session.playbackIdle()
        assertEquals("interrupted", summaries().single()["outcome"])
    }

    @Test fun `playback counter excludes earlier queued audio and handles wrap`() {
        assertFalse(playbackHasPassed(90, 100))
        assertFalse(playbackHasPassed(100, 100))
        assertTrue(playbackHasPassed(101, 100))
        assertTrue(playbackHasPassed(1, 0xffffffffL))
        assertFalse(playbackHasPassed(-2, 0xffffffffL))
    }

    @Test fun `playback drain target accepts equality and handles wrap`() {
        assertTrue(playbackHasReached(100, 100))
        assertTrue(playbackHasReached(101, 100))
        assertFalse(playbackHasReached(99, 100))
        assertTrue(playbackHasReached(0, 0xffffffffL))
    }

    @Test fun `session close is terminal and keeps the original failure reason`() {
        val session = session()
        session.turn()
        session.close("disconnected")
        session.close("closed")
        assertNull(session.turn())
        assertEquals("disconnected", summaries().single()["outcome"])
        assertEquals(1, events.count { it["event"] == "session_end" })
    }

    @Test fun `timing records have no provider IDs or conversation payload`() {
        val turn = session().turn()!!
        turn.speech(1000, "rms")
        turn.tool().event("tool_response_sent", mapOf("context_chars" to 200))
        turn.complete()
        val forbidden = setOf("query", "text", "audio", "apiKey", "providerCallId", "document")
        assertTrue(events.all { record -> record.keys.none { it in forbidden } })
    }
}
