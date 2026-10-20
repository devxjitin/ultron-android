package com.assistant.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Checks a GitHub token and reads whose it is. The tools themselves come from GitHub's MCP server. */
object GithubClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** The login a token belongs to, or an error message the UI can show. */
    suspend fun validate(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(JSONObject(response.body?.string().orEmpty()).getString("login"))
                    401 -> Result.failure(IllegalStateException("GitHub rejected this sign-in. Try again."))
                    else -> Result.failure(IllegalStateException("GitHub answered HTTP ${response.code}."))
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not reach GitHub. Check the internet connection."))
        }
    }
}
