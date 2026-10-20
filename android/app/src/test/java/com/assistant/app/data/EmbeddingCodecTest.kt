package com.assistant.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingCodecTest {
    @Test
    fun roundTripPreservesExactFloatValues() {
        val vector = FloatArray(768) { (it - 384) / 7.31f }
        vector[0] = Float.MIN_VALUE
        vector[1] = -0f
        assertArrayEquals(vector, EmbeddingCodec.decode(EmbeddingCodec.encode(vector)), 0f)
    }

    @Test
    fun emptyVectorRoundTrips() {
        assertEquals(0, EmbeddingCodec.decode(EmbeddingCodec.encode(FloatArray(0))).size)
    }

    @Test
    fun encodedFormIsMuchSmallerThanJsonDoubles() {
        val vector = FloatArray(768) { 0.123456789f * it }
        val json = vector.joinToString(",", "[", "]") { it.toDouble().toString() }
        assertTrue(EmbeddingCodec.encode(vector).length * 3 < json.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedBytesAreRejected() {
        EmbeddingCodec.decode(java.util.Base64.getEncoder().encodeToString(ByteArray(7)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBase64IsRejected() {
        EmbeddingCodec.decode("not base64 !!")
    }
}
