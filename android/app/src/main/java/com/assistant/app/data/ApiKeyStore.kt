package com.assistant.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Holds the GitHub connector token, encrypted at rest. Gemini keys are not stored here: they come from the build.
 *
 * Opening the encrypted preferences touches the Android Keystore, which can be slow and can fail
 * (e.g. after a restore or OS update). A failure leaves the store unavailable: reads return null
 * and writes return false, instead of crashing the caller. Callers should first use it off the
 * main thread.
 */
class ApiKeyStore internal constructor(prefsProvider: () -> SharedPreferences) {

    constructor(context: Context) : this({
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "assistant_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    })

    private val prefs: SharedPreferences? by lazy {
        try {
            prefsProvider()
        } catch (e: Exception) {
            Log.e("AssistantError", "Secure key storage is unavailable", e)
            null
        }
    }

    /** Fine-grained, read-only token for the GitHub connector. */
    fun getGithubToken(): String? = read(KEY_GITHUB_TOKEN)

    fun setGithubToken(token: String): Boolean = write { it.putString(KEY_GITHUB_TOKEN, token.trim()) }

    fun clearGithubToken(): Boolean = write { it.remove(KEY_GITHUB_TOKEN) }

    private fun read(name: String): String? = try {
        prefs?.getString(name, null)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.e("AssistantError", "Couldn't read a stored key", e)
        null
    }

    private fun write(change: (SharedPreferences.Editor) -> SharedPreferences.Editor): Boolean {
        val store = prefs ?: return false
        return try {
            change(store.edit()).commit()
        } catch (e: Exception) {
            Log.e("AssistantError", "Couldn't write a stored key", e)
            false
        }
    }

    private companion object {
        const val KEY_GITHUB_TOKEN = "github_token"
    }
}
