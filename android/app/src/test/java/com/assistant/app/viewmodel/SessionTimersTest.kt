package com.assistant.app.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class SessionTimersTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val events = CopyOnWriteArrayList<String>()

    @After fun tearDown() = job.cancel()

    private fun timers(
        idle: Long = 60, turn: Long = 60, max: Long = 200, warn: Long = 100, poll: Long = 10, stall: Long = 60
    ) = SessionTimers(
        scope,
        onIdleTimeout = { events += "idle" },
        onTurnTimeout = { events += "turn" },
        onSessionWarning = { events += "warning" },
        onSessionLimit = { events += "limit" },
        idleTimeoutMs = idle, turnResponseTimeoutMs = turn, maxSessionMs = max, sessionWarningMs = warn,
        speakingPollMs = poll, speakingStallMs = stall, now = System::currentTimeMillis
    )

    private fun waitFor(ms: Long) = Thread.sleep(ms)

    @Test fun `idle timeout fires once when nothing resets it`() {
        timers().resetIdle()
        waitFor(250)
        assertEquals(listOf("idle"), events.toList())
    }

    @Test fun `resetting the idle timer pushes it back`() {
        val t = timers(idle = 150)
        t.resetIdle()
        waitFor(100)
        t.resetIdle()
        waitFor(100)
        assertEquals(emptyList<String>(), events.toList())
        waitFor(200)
        assertEquals(listOf("idle"), events.toList())
    }

    @Test fun `cancelled idle timer never fires`() {
        val t = timers()
        t.resetIdle()
        t.cancelIdle()
        waitFor(200)
        assertTrue(events.isEmpty())
    }

    @Test fun `turn timeout fires and can be cancelled`() {
        val t = timers()
        t.startTurnTimeout()
        t.cancelTurnTimeout()
        waitFor(150)
        assertTrue(events.isEmpty())
        t.startTurnTimeout()
        waitFor(200)
        assertEquals(listOf("turn"), events.toList())
    }

    @Test fun `session limit warns first and then ends`() {
        timers(max = 200, warn = 100).startSessionLimit()
        waitFor(500)
        assertEquals(listOf("warning", "limit"), events.toList())
    }

    @Test fun `cancelling the session limit stops both`() {
        val t = timers(max = 200, warn = 100)
        t.startSessionLimit()
        t.cancelSessionLimit()
        waitFor(400)
        assertTrue(events.isEmpty())
    }

    @Test fun `a stalled reply is reported once`() {
        val stalled = AtomicInteger()
        timers().startSpeakingWatchdog({ true }, { 5L }, { false }, { stalled.incrementAndGet() })
        waitFor(400)
        assertEquals(1, stalled.get())
    }

    @Test fun `moving playback is not a stall`() {
        val stalled = AtomicInteger()
        val head = AtomicInteger()
        val t = timers()
        t.startSpeakingWatchdog({ true }, { head.incrementAndGet().toLong() }, { false }, { stalled.incrementAndGet() })
        waitFor(300)
        assertEquals(0, stalled.get())
        t.cancelSpeakingWatchdog()
    }

    @Test fun `a running tool is not a stall`() {
        val stalled = AtomicInteger()
        val t = timers()
        t.startSpeakingWatchdog({ true }, { 5L }, { true }, { stalled.incrementAndGet() })
        waitFor(300)
        assertEquals(0, stalled.get())
        t.cancelSpeakingWatchdog()
    }

    @Test fun `new audio keeps the watchdog quiet`() {
        val stalled = AtomicInteger()
        val t = timers(stall = 150)
        t.startSpeakingWatchdog({ true }, { 5L }, { false }, { stalled.incrementAndGet() })
        repeat(6) { t.noteAudioChunk(); waitFor(40) }
        assertEquals(0, stalled.get())
        t.cancelSpeakingWatchdog()
    }

    @Test fun `the watchdog stops quietly when speaking ends or the player is gone`() {
        val stalled = AtomicInteger()
        timers().startSpeakingWatchdog({ false }, { 5L }, { false }, { stalled.incrementAndGet() })
        timers().startSpeakingWatchdog({ true }, { null }, { false }, { stalled.incrementAndGet() })
        waitFor(300)
        assertEquals(0, stalled.get())
    }
}
