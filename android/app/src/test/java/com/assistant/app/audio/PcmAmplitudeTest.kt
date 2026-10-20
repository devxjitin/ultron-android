package com.assistant.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmAmplitudeTest {

    private fun pcm16Of(vararg samples: Short): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun `empty buffer is zero amplitude`() {
        assertEquals(0f, rmsAmplitude(ByteArray(0)), 0.0001f)
    }

    @Test
    fun `single unpaired byte is silence rather than NaN`() {
        assertEquals(0f, rmsAmplitude(byteArrayOf(0x7F)), 0f)
    }

    @Test
    fun `all-zero samples are zero amplitude`() {
        val silence = pcm16Of(0, 0, 0, 0)
        assertEquals(0f, rmsAmplitude(silence), 0.0001f)
    }

    @Test
    fun `full-scale samples are near max amplitude`() {
        val loud = pcm16Of(Short.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE)
        assertEquals(1f, rmsAmplitude(loud), 0.001f)
    }

    @Test
    fun `result is always within 0 to 1`() {
        val samples = pcm16Of(12000, -30000, 5000, -12000, 32000)
        val amp = rmsAmplitude(samples)
        assertTrue(amp in 0f..1f)
    }

    @Test
    fun `odd trailing byte is ignored, not crashed on`() {
        val oddLength = pcm16Of(1000, 2000) + byteArrayOf(0x7F)
        // Should not throw -- the trailing unpaired byte is simply not read as a sample.
        rmsAmplitude(oddLength)
    }

    @Test
    fun `microphone gain changes samples and clips safely`() {
        val quiet = pcm16Of(1000, -1000)
        applyPcmGain(quiet, 0.6f)
        assertEquals(pcm16Of(600, -600).toList(), quiet.toList())

        val loud = pcm16Of(20000, -20000)
        applyPcmGain(loud, 1.8f)
        assertEquals(pcm16Of(Short.MAX_VALUE, Short.MIN_VALUE).toList(), loud.toList())
    }

    @Test
    fun `medium gain preserves the original audio`() {
        val original = pcm16Of(1234, -5678) + byteArrayOf(0x7F)
        val result = original.copyOf()
        applyPcmGain(result, 1f)
        assertEquals(original.toList(), result.toList())
    }
}
