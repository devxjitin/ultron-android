package com.assistant.app.network

import com.assistant.app.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Talks to the Ultron API on AWS. Every call carries the user's Firebase ID token, which API Gateway checks before any
 * Lambda runs. Without a base URL in the build, or without a signed-in user, callers fall back to the older secret-URL
 * servers until those are retired.
 */
object ApiClient {
    private const val TOKEN_WAIT_SECONDS = 10L

    val baseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun url(path: String): String = baseUrl + path

    /**
     * "Bearer <token>" for the signed-in (anonymous at first) user, or null when there is none. Blocks, so call it off the
     * main thread; the SDK returns a cached token until it is close to expiring.
     */
    fun authHeader(): String? = runCatching {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: Tasks.await(auth.signInAnonymously(), TOKEN_WAIT_SECONDS, TimeUnit.SECONDS).user
        val token = user?.let { Tasks.await(it.getIdToken(false), TOKEN_WAIT_SECONDS, TimeUnit.SECONDS).token }
        token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }.getOrNull()

    /** Headers for [McpClient]; empty when the caller is not signed in, so the request is refused rather than leaked. */
    fun authHeaders(): Map<String, String> = authHeader()?.let { mapOf("Authorization" to it) }.orEmpty()
}
