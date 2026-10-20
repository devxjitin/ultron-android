package com.assistant.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What the user must see to approve the sign-in on GitHub's own page. */
data class GithubDeviceCode(
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int,
    val deviceCode: String
)

sealed interface GithubPoll {
    data class Token(val value: String) : GithubPoll
    data object Pending : GithubPoll
    data object SlowDown : GithubPoll
    data class Failed(val message: String) : GithubPoll
}

/**
 * GitHub's OAuth device flow: the user approves on github.com in the phone's browser, so no token is
 * ever typed or pasted. Only the public OAuth App client id is needed (no secret).
 */
object GithubDeviceFlow {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun start(clientId: String, scope: String): Result<GithubDeviceCode> = withContext(Dispatchers.IO) {
        try {
            val json = post("https://github.com/login/device/code", FormBody.Builder().add("client_id", clientId).add("scope", scope).build())
            if (json.has("error")) {
                return@withContext Result.failure(IllegalStateException(describe(json.optString("error"))))
            }
            Result.success(
                GithubDeviceCode(
                    userCode = json.getString("user_code"),
                    verificationUri = json.getString("verification_uri"),
                    expiresInSec = json.optInt("expires_in", 900),
                    intervalSec = json.optInt("interval", 5).coerceAtLeast(5),
                    deviceCode = json.getString("device_code")
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not reach GitHub. Check the internet connection."))
        }
    }

    suspend fun poll(clientId: String, deviceCode: String): GithubPoll = withContext(Dispatchers.IO) {
        try {
            val json = post(
                "https://github.com/login/oauth/access_token",
                FormBody.Builder().add("client_id", clientId).add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()
            )
            val token = json.optString("access_token")
            when {
                token.isNotEmpty() -> GithubPoll.Token(token)
                json.optString("error") == "authorization_pending" -> GithubPoll.Pending
                json.optString("error") == "slow_down" -> GithubPoll.SlowDown
                else -> GithubPoll.Failed(describe(json.optString("error")))
            }
        } catch (e: Exception) {
            GithubPoll.Pending // a dropped connection should not end the sign-in; the next poll retries
        }
    }

    private fun post(url: String, body: FormBody): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").post(body).build()
        http.newCall(request).execute().use { response -> return JSONObject(response.body?.string().orEmpty().ifBlank { "{}" }) }
    }

    private fun describe(error: String) = when (error) {
        "expired_token" -> "The sign-in code expired. Please try again."
        "access_denied" -> "Sign-in was cancelled on GitHub."
        "device_flow_disabled" -> "Device sign-in is turned off for this GitHub app. Enable \"Device Flow\" in its settings."
        "incorrect_client_credentials" -> "The GitHub client id is not valid."
        else -> "GitHub sign-in failed ($error)."
    }
}
