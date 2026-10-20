package com.assistant.app.util

import com.assistant.app.network.GeminiEmbeddingClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {

    @Test
    fun `no internet variants map to the no-internet message`() {
        val expected = "No internet connection. Please check Wi-Fi and try again."
        assertEquals(expected, UserFacingError.forThrowable("ctx", UnknownHostException()))
        assertEquals(expected, UserFacingError.forThrowable("ctx", ConnectException()))
    }

    @Test
    fun `timeout maps to the slow-connection message`() {
        assertEquals(
            "Connection is taking too long. Please check your internet and try again.",
            UserFacingError.forThrowable("ctx", SocketTimeoutException())
        )
    }

    @Test
    fun `ssl failure maps to the secure-connection message`() {
        assertEquals(
            "Couldn't securely connect. Please try again.",
            UserFacingError.forThrowable("ctx", SSLException("bad cert"))
        )
    }

    @Test
    fun `generic IOException maps to the connection-problem message`() {
        assertEquals(
            "Connection problem. Please check your internet and try again.",
            UserFacingError.forThrowable("ctx", IOException("boom"))
        )
    }

    @Test
    fun `unrecognized throwable falls back to a generic message`() {
        assertEquals(
            "Something went wrong. Please try again.",
            UserFacingError.forThrowable("ctx", IllegalStateException("boom"))
        )
    }

    @Test
    fun `app-authored user messages pass through unchanged`() {
        assertEquals("Pick another file.", UserFacingError.forThrowable("ctx", UserMessageException("Pick another file.")))
        assertEquals("Add some text.", UserFacingError.forThrowable("ctx", UserInputException("Add some text.")))
    }

    @Test
    fun `plain argument exception stays generic`() {
        assertEquals(
            "Something went wrong. Please try again.",
            UserFacingError.forThrowable("ctx", IllegalArgumentException("internal detail"))
        )
    }

    @Test
    fun `401 and 403 read as an app setup problem, not the caller's fault`() {
        val expected = "There's a problem with the app setup. Please contact support."
        assertEquals(expected, UserFacingError.forHttpFailure("ctx", 401, null))
        assertEquals(expected, UserFacingError.forHttpFailure("ctx", 403, null))
    }

    @Test
    fun `429 reads as rate limiting`() {
        assertEquals(
            "Too many requests right now. Please wait a moment and try again.",
            UserFacingError.forHttpFailure("ctx", 429, null)
        )
    }

    @Test
    fun `5xx reads as a temporary service outage`() {
        assertEquals(
            "The service is temporarily unavailable. Please try again shortly.",
            UserFacingError.forHttpFailure("ctx", 503, null)
        )
    }

    @Test
    fun `other http codes fall back to a generic message`() {
        assertEquals(
            "Something went wrong. Please try again.",
            UserFacingError.forHttpFailure("ctx", 418, null)
        )
    }

    @Test
    fun `embedding http failures use their status message, not the generic connection one`() {
        assertEquals(
            "There's a problem with the app setup. Please contact support.",
            UserFacingError.forThrowable("ctx", GeminiEmbeddingClient.HttpFailure(401, "bad key"))
        )
        assertEquals(
            "Too many requests right now. Please wait a moment and try again.",
            UserFacingError.forThrowable("ctx", GeminiEmbeddingClient.HttpFailure(429, "slow down"))
        )
    }

    @Test
    fun `exhausted retries describe the underlying failure`() {
        assertEquals(
            "The service is temporarily unavailable. Please try again shortly.",
            UserFacingError.forThrowable(
                "ctx",
                GeminiEmbeddingClient.DocumentEmbeddingRetryExhausted(GeminiEmbeddingClient.HttpFailure(503, "down"))
            )
        )
        assertEquals(
            "No internet connection. Please check Wi-Fi and try again.",
            UserFacingError.forThrowable(
                "ctx", GeminiEmbeddingClient.DocumentEmbeddingRetryExhausted(UnknownHostException())
            )
        )
    }

    @Test
    fun `server error message is always the same short sentence regardless of raw content`() {
        val result = UserFacingError.forServerErrorMessage("ctx", "some raw internal detail")
        assertTrue(result.isNotBlank())
        assertEquals("The assistant ran into a problem. Please try again.", result)
    }
}
