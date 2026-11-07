package com.assistant.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

/**
 * Starts the Firebase pieces that every request leans on: App Check (proof the call comes from the genuine app) and an
 * anonymous sign-in (a stable user id without asking for an account). Without a Firebase config the app skips both.
 * Gemini never goes through Firebase; it always uses the key the user saved.
 */
class UltronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The Schedule page reads this list; it is a small local file.
        runCatching { com.assistant.app.data.ScheduledPrompts.load(this) }
        if (runCatching { FirebaseApp.getApps(this).isEmpty() }.getOrDefault(true)) return
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
