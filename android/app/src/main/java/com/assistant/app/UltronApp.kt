package com.assistant.app

import android.app.Application
import android.util.Log
import com.assistant.app.network.FirebaseGemini
import com.google.firebase.auth.FirebaseAuth

/**
 * Starts the Firebase pieces that every request leans on: App Check (proof the call comes from the genuine app) and an
 * anonymous sign-in (a stable user id without asking for an account). Without a Firebase config the app skips both
 * and keeps working with the on-device Gemini key.
 */
class UltronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!FirebaseGemini.isAvailable(this)) return
        runCatching { AppCheckInstaller.install() }.onFailure { Log.w(TAG, "App Check setup failed", it) }
        runCatching {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnFailureListener { Log.w(TAG, "Anonymous sign-in failed", it) }
            }
        }.onFailure { Log.w(TAG, "Auth setup failed", it) }
    }

    private companion object {
        const val TAG = "UltronApp"
    }
}
