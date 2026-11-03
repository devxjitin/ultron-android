package com.assistant.app.network

import com.assistant.app.data.LicenseStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The license routes of the Ultron API (mcp/license). Every call blocks, so run it off the main thread. */
class LicenseClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Binds [key] to this device (moving it off any other one) and returns its status. */
    fun activate(key: String): LicenseStatus = call("/v1/license/activate", key)

    /** Re-reads [key] without moving it; inactive once the key expired, was revoked or moved to another device. */
    fun status(key: String): LicenseStatus = call("/v1/license/status", key)

    private fun call(path: String, key: String): LicenseStatus {
        val auth = ApiClient.authHeader() ?: throw IOException("Not signed in")
        val request = Request.Builder()
            .url(ApiClient.url(path))
            .header("Authorization", auth)
            .apply { ApiClient.appCheckToken()?.let { header(ApiClient.APP_CHECK_HEADER, it) } }
            .post(JSONObject().put("key", key).toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) throw LicenseException(response.code, json.optString("error", "HTTP ${response.code}"))
            return LicenseStatus.fromJson(json)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** [code] is the HTTP status; [message] is the Lambda's error id such as "unknown_key" or "moved_recently". */
class LicenseException(val code: Int, message: String) : IOException(message)
