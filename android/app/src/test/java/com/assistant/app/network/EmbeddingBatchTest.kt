package com.assistant.app.network

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingBatchTest {
    @Test fun `batch body has one request per text in order`() {
        val body = JSONObject(GeminiEmbeddingClient.batchBody(listOf("a", "b"), GeminiEmbeddingClient.TaskType.DOCUMENT))
        val requests = body.getJSONArray("requests")
        assertEquals(2, requests.length())
        val second = requests.getJSONObject(1)
        assertEquals("models/${GeminiEmbeddingClient.MODEL}", second.getString("model"))
        assertEquals("RETRIEVAL_DOCUMENT", second.getString("taskType"))
        assertEquals(768, second.getInt("outputDimensionality"))
        assertEquals("b", second.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test fun `parse returns vectors in request order`() {
        val raw = """{"embeddings":[{"values":[1.0,2.0]},{"values":[3.0]}]}"""
        val vectors = GeminiEmbeddingClient.parseBatch(raw, 2)
        assertArrayEquals(floatArrayOf(1f, 2f), vectors[0], 0f)
        assertArrayEquals(floatArrayOf(3f), vectors[1], 0f)
    }

    @Test(expected = IllegalStateException::class)
    fun `a missing vector is an error instead of shifting the rest`() {
        GeminiEmbeddingClient.parseBatch("""{"embeddings":[{"values":[1.0]}]}""", 2)
    }

    @Test fun `batch size stays within the API limit`() {
        assert(GeminiEmbeddingClient.BATCH_SIZE in 1..100)
    }
}
