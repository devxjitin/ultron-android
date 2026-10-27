package com.assistant.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BargeInGateTest {
    private val chunk = ByteArray(8) { 1 }

    private fun isSilence(chunks: List<ByteArray>) = chunks.size == 1 && chunks[0].all { it == 0.toByte() }

    @Test
    fun `passes audio through while the assistant is quiet`() {
        val gate = BargeInGate()
        assertEquals(listOf(chunk), gate.process(chunk, 0.3f, gated = false))
    }

    @Test
    fun `short loud noise is replaced by silence while gated`() {
        val gate = BargeInGate()
        repeat(7) { assertTrue(isSilence(gate.process(chunk, 0.3f, gated = true))) }
        // Quiet again, so the run of loud chunks restarts.
        assertTrue(isSilence(gate.process(chunk, 0.0f, gated = true)))
        repeat(7) { assertTrue(isSilence(gate.process(chunk, 0.3f, gated = true))) }
    }

    @Test
    fun `sustained loud speech opens the gate and replays the buffered chunks`() {
        val gate = BargeInGate(openAfterChunks = 4)
        repeat(3) { assertTrue(isSilence(gate.process(chunk, 0.3f, gated = true))) }
        assertEquals(4, gate.process(chunk, 0.3f, gated = true).size)
        assertEquals(listOf(chunk), gate.process(chunk, 0.3f, gated = true))
    }

    @Test
    fun `a loud room raises the bar so its steady noise never opens the gate`() {
        val gate = BargeInGate(openAfterChunks = 4)
        repeat(300) { gate.process(chunk, 0.2f, gated = false) }
        repeat(20) { assertTrue(isSilence(gate.process(chunk, 0.25f, gated = true))) }
    }

    @Test
    fun `gate closes again after the speech stops`() {
        val gate = BargeInGate(openAfterChunks = 2, closeAfterQuietChunks = 3)
        gate.process(chunk, 0.3f, gated = true)
        gate.process(chunk, 0.3f, gated = true)
        repeat(3) { gate.process(chunk, 0.0f, gated = true) }
        assertTrue(isSilence(gate.process(chunk, 0.0f, gated = true)))
    }
}
