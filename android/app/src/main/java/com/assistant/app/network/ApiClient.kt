package com.assistant.app.network

import com.assistant.app.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Talks to the Ultron API on AWS. Every call carries the user's Firebase ID token, which API Gateway checks before any
 * Lambda runs. Without a base URL in the build, or without a signed-in user, callers send nothing and report that the
 * service is not available.
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

    /**
     * The App Check token that proves the request comes from the genuine app, or null when none could be fetched (the
     * server then decides whether to refuse). Blocks, so call it off the main thread; the SDK caches the token.
     */
    fun appCheckToken(): String? = runCatching {
        Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(false), TOKEN_WAIT_SECONDS, TimeUnit.SECONDS).token
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Headers for [McpClient]; empty when the caller is not signed in, so the request is refused rather than leaked. */
    fun authHeaders(): Map<String, String> {
        val auth = authHeader() ?: return emptyMap()
        return buildMap {
            put("Authorization", auth)
            appCheckToken()?.let { put(APP_CHECK_HEADER, it) }
        }
    }

    const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
}
