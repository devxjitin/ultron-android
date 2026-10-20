package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingModelMigrationTest {

    private fun chunk(model: String) = RagChunk(
        docId = "document",
        chunkIndex = 0,
        text = "text",
        embedding = floatArrayOf(1f),
        embeddingModel = model
    )

    @Test
    fun `only the configured embedding model is current`() {
        assertFalse(requiresEmbeddingMigration(listOf(chunk(GeminiEmbeddingClient.MODEL))))
        assertTrue(requiresEmbeddingMigration(listOf(chunk("gemini-embedding-001"))))
    }
}
