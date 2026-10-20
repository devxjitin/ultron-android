package com.assistant.app.network

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.assistant.app.data.GoogleService
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Gets short-lived Google access tokens through Google Play services. Nothing secret is stored by the app:
 * once the user has allowed a service, asking again returns a fresh token without any screen.
 */
object GoogleAuth {
    sealed interface Outcome {
        data class Token(val accessToken: String, val email: String?) : Outcome
        /** The user still has to pick an account and allow access; launch [intent] from an activity. */
        data class NeedsConsent(val intent: PendingIntent) : Outcome
        /** [network] is true when the phone simply could not reach Google; that says nothing about the sign-in. */
        data class Failed(val message: String, val network: Boolean = false) : Outcome
    }

    /** [email] picks a known account; null lets the user choose any Google account on the phone. */
    suspend fun authorize(context: Context, service: GoogleService, email: String?): Outcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(service.scope), Scope(Scopes.EMAIL)))
            // Only a real address picks an account; anything else would make Google reject the request.
            .apply { if (email != null && '@' in email) setAccount(Account(email, "com.google")) }
            .build()
        return suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context).authorize(request)
                .addOnSuccessListener { if (cont.isActive) cont.resume(outcomeOf(it)) }
                .addOnFailureListener {
                    val network = (it as? ApiException)?.statusCode == CommonStatusCodes.NETWORK_ERROR
                    if (cont.isActive) cont.resume(Outcome.Failed(it.message ?: "Google sign-in failed.", network))
                }
        }
    }

    /** Reads the answer the consent screen returned to the activity. */
    fun outcomeFromIntent(context: Context, data: Intent?): Outcome = try {
        outcomeOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Outcome.Failed(e.message ?: "Google sign-in was cancelled.")
    }

    /** The signed-in account's email address, read with the access token; null if Google does not say. */
    suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://openidconnect.googleapis.com/v1/userinfo")
                .header("Authorization", "Bearer $accessToken").build()
            HttpClients.shared.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty()).optString("email").takeIf { '@' in it }
            }
        }.getOrNull()
    }

    private fun outcomeOf(result: AuthorizationResult): Outcome {
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) return Outcome.NeedsConsent(pending)
        val token = result.accessToken ?: return Outcome.Failed("Google did not return an access token.")
        return Outcome.Token(token, result.toGoogleSignInAccount()?.email)
    }
}
