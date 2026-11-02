package com.assistant.app.network

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.assistant.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in on top of Firebase Auth. The subscription belongs to the Google account, so it survives a reinstall.
 * An existing anonymous user is upgraded in place (same uid) when possible; if that Google account already has a user,
 * the app switches to it instead.
 */
object GoogleAuth {
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** The signed-in Google user, or null while there is none (anonymous users do not count). */
    fun currentGoogleUser(): FirebaseUser? =
        runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?.takeIf { user -> !user.isAnonymous && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } }

    /** Shows the Google account picker and signs in. Throws when the user backs out or sign-in fails. */
    suspend fun signIn(activity: Activity): FirebaseUser {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val firebaseCredential = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        val auth = FirebaseAuth.getInstance()
        val anonymous = auth.currentUser?.takeIf { it.isAnonymous }
        val user = try {
            anonymous?.linkWithCredential(firebaseCredential)?.await()?.user
                ?: auth.signInWithCredential(firebaseCredential).await().user
        } catch (e: FirebaseAuthUserCollisionException) {
            auth.signInWithCredential(firebaseCredential).await().user
        } ?: error("Sign-in returned no user")
        // The cached ID token still says "anonymous" after a link; the billing API needs the new one.
        user.getIdToken(true).await()
        return user
    }

    /** Signs out of Firebase and clears the remembered Google account, so the picker shows again next time. */
    suspend fun signOut(activity: Activity) {
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
    }
}
