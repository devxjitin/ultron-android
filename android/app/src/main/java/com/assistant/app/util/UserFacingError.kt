package com.assistant.app.util

import android.util.Log
import com.assistant.app.network.GeminiEmbeddingClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Exceptions whose message is already written for end users and is safe to show as-is.
 * Subclasses keep the original exception types (state vs. argument) so existing handling is unchanged.
 */
class UserMessageException(message: String) : IllegalStateException(message)
class UserInputException(message: String) : IllegalArgumentException(message)

/**
 * Turns technical exceptions/HTTP failures into short, kiosk-friendly sentences for the UI,
 * while logging the real technical detail via Log.e so it's still visible in Logcat.
 */
object UserFacingError {

    private const val TAG = "AssistantError"

    fun forThrowable(context: String, t: Throwable): String {
        Log.e(TAG, "$context: ${t.javaClass.simpleName}: ${t.message}", t)
        return messageFor(t)
    }

    private fun messageFor(t: Throwable): String {
        return when (t) {
            is UserMessageException, is UserInputException ->
                t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
            // Retries ran out: describe the failure that kept recurring, not the retry itself.
            is GeminiEmbeddingClient.DocumentEmbeddingRetryExhausted ->
                t.cause?.let(::messageFor) ?: "Something went wrong. Please try again."
            is GeminiEmbeddingClient.HttpFailure -> httpMessage(t.statusCode)
            is UnknownHostException, is ConnectException ->
                "No internet connection. Please check Wi-Fi and try again."
            is SocketTimeoutException ->
                "Connection is taking too long. Please check your internet and try again."
            is SSLException ->
                "Couldn't securely connect. Please try again."
            is IOException ->
                "Connection problem. Please check your internet and try again."
            else ->
                "Something went wrong. Please try again."
        }
    }

    fun forHttpFailure(context: String, code: Int, rawMessage: String?): String {
        Log.e(TAG, "$context: HTTP $code $rawMessage")
        return httpMessage(code)
    }

    private fun httpMessage(code: Int): String {
        return when (code) {
            401, 403 -> "There's a problem with the app setup. Please contact support."
            429 -> "Too many requests right now. Please wait a moment and try again."
            in 500..599 -> "The service is temporarily unavailable. Please try again shortly."
            else -> "Something went wrong. Please try again."
        }
    }

    fun forServerErrorMessage(context: String, rawMessage: String): String {
        Log.e(TAG, "$context: $rawMessage")
        return "The assistant ran into a problem. Please try again."
    }
}
