package com.assistant.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLevelsTest {
    private fun tone(frames: Int, amplitude: Short): ByteArray {
        val bytes = ByteArray(frames * 2)
        for (i in 0 until frames) {
            bytes[i * 2] = (amplitude.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((amplitude.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun `nothing recorded is silence`() {
        assertEquals(0f, PlaybackLevels(100).levelAt(50), 0f)
    }

    @Test
    fun `the level follows where playback is, not when audio was queued`() {
        val levels = PlaybackLevels(100)
        levels.add(0, tone(100, 0))
        levels.add(100, tone(100, 8000))
        assertEquals(0f, levels.levelAt(50), 0f)
        assertTrue(levels.levelAt(150) > 0.5f)
    }

    @Test
    fun `a slice keeps its level for every lookup inside it`() {
        val levels = PlaybackLevels(100)
        levels.add(0, tone(100, 8000))
        val first = levels.levelAt(10)
        assertEquals(first, levels.levelAt(10), 0f)
        assertEquals(first, levels.levelAt(99), 0f)
    }

    @Test
    fun `after the last slice has played the level is zero`() {
        val levels = PlaybackLevels(100)
        levels.add(0, tone(100, 8000))
        assertEquals(0f, levels.levelAt(100), 0f)
        assertEquals(0f, levels.levelAt(500), 0f)
    }

    @Test
    fun `a chunk longer than a slice is split so the level can change inside it`() {
        val levels = PlaybackLevels(100)
        levels.add(0, tone(100, 8000) + tone(100, 0))
        assertTrue(levels.levelAt(50) > 0.5f)
        assertEquals(0f, levels.levelAt(150), 0f)
    }

    @Test
    fun `clear forgets everything queued`() {
        val levels = PlaybackLevels(100)
        levels.add(0, tone(100, 8000))
        levels.clear()
        assertEquals(0f, levels.levelAt(10), 0f)
    }
}
