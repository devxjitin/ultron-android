package com.assistant.app.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Sends a skill or connector request or a bug report to the Ultron API (mcp/feature-requests), which emails the developer. Blocks. */
class FeatureRequestClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Throws [FeatureRequestException] with a message fit to show the user when it could not be sent. */
    fun send(type: String, name: String, details: String, examples: String, email: String, appVersion: String, device: String = "") {
        if (!ApiClient.isConfigured) throw FeatureRequestException("Sending is not set up in this build.")
        val auth = ApiClient.authHeader() ?: throw FeatureRequestException("Could not sign in. Check the internet and try again.")
        val body = JSONObject()
            .put("type", type).put("name", name).put("details", details)
            .put("examples", examples).put("email", email).put("appVersion", appVersion)
            .apply { if (device.isNotBlank()) put("device", device) }
        val request = Request.Builder()
            .url(ApiClient.url("/v1/requests"))
            .header("Authorization", auth)
            .apply { ApiClient.appCheckToken()?.let { header(ApiClient.APP_CHECK_HEADER, it) } }
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) return
                throw FeatureRequestException(message(response.code, response.body?.string().orEmpty()))
            }
        } catch (e: IOException) {
            if (e is FeatureRequestException) throw e
            throw FeatureRequestException("Could not reach the server. Check the internet and try again.")
        }
    }

    private fun message(code: Int, text: String): String {
        val error = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
        return when {
            code == 429 -> "You have sent a few requests today already. Please try again tomorrow."
            error == "bad_email" -> "That email address does not look right."
            error == "bad_name" || error == "bad_details" -> "Please add a title and a few words of detail."
            error == "bad_type" -> "Bug reports need a server update. Please email the developer for now."
            else -> "Could not send it right now. Please try again later."
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

class FeatureRequestException(message: String) : IOException(message)
