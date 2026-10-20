package com.assistant.app.network

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class EmbeddingRetryPolicyTest {
    private fun http(code: Int, retryAfterMs: Long? = null) =
        GeminiEmbeddingClient.HttpFailure(code, "failed", retryAfterMs)

    private fun retryable(e: Throwable) = GeminiEmbeddingClient.isRetryableEmbeddingFailure(e)

    @Test fun `authentication and validation failures are not retried`() {
        listOf(400, 401, 403, 404).forEach { assertFalse("HTTP $it", retryable(http(it))) }
    }

    @Test fun `throttling, timeouts and service errors are retried`() {
        listOf(408, 429, 500, 503).forEach { assertTrue("HTTP $it", retryable(http(it))) }
        assertTrue(retryable(SocketTimeoutException()))
        assertTrue(retryable(UnknownHostException()))
        assertTrue(retryable(IOException("reset")))
    }

    @Test fun `unparseable or unexpected failures are not retried`() {
        assertFalse(retryable(JSONException("bad body")))
        assertFalse(retryable(IllegalStateException()))
    }

    @Test fun `retry delay honors Retry-After, is capped, and defaults otherwise`() {
        assertEquals(5_000L, GeminiEmbeddingClient.embeddingRetryDelayMs(http(503)))
        assertEquals(5_000L, GeminiEmbeddingClient.embeddingRetryDelayMs(IOException()))
        assertEquals(12_000L, GeminiEmbeddingClient.embeddingRetryDelayMs(http(429, 12_000L)))
        assertEquals(60_000L, GeminiEmbeddingClient.embeddingRetryDelayMs(http(429, 900_000L)))
        assertEquals(0L, GeminiEmbeddingClient.embeddingRetryDelayMs(http(429, 0L)))
    }
}
