package com.assistant.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class CallbackGateTest {
    @Test fun `open gate runs callbacks`() {
        val gate = CallbackGate()
        var calls = 0
        gate.run { calls++ }
        gate.run { calls++ }
        assertEquals(2, calls)
    }

    @Test fun `closed gate drops callbacks queued earlier`() {
        val gate = CallbackGate()
        var calls = 0
        val queued = listOf({ gate.run { calls++ } }, { gate.run { calls++ } })
        gate.close()
        queued.forEach { it() }
        assertEquals(0, calls)
    }

    @Test fun `a new gate is independent of an old closed one`() {
        val old = CallbackGate().also { it.close() }
        val fresh = CallbackGate()
        var calls = 0
        old.run { calls++ }
        fresh.run { calls++ }
        assertEquals(1, calls)
    }
}
