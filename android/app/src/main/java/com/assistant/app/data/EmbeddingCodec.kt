package com.assistant.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Compact storage for embedding vectors: little-endian float32 bytes, Base64 encoded. */
internal object EmbeddingCodec {
    fun encode(embedding: FloatArray): String {
        val buffer = ByteBuffer.allocate(embedding.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(embedding)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decode(encoded: String): FloatArray {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size % Float.SIZE_BYTES == 0) { "Embedding byte length is not a multiple of 4." }
        val floats = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }
}
