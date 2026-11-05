package com.assistant.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Persists the Gemini API key the user enters in-app, encrypted at rest, so it never has to be
 * baked into the APK. This is the primary source for the key; the BuildConfig value from
 * local.properties (if any) is only used as a fallback when nothing has been saved here yet.
 *
 * Opening the encrypted preferences touches the Android Keystore, which can be slow and can fail
 * (e.g. after a restore or OS update). A failure leaves the store unavailable: reads return null
 * and writes return false, instead of crashing the caller. Callers should first use it off the
 * main thread.
 */
class ApiKeyStore internal constructor(prefsProvider: () -> SharedPreferences) : ApiKeyStorage {

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

    override fun getApiKey(): String? = read(KEY_API_KEY)

    /** Writes synchronously and reports whether the value was actually committed. */
    override fun setApiKey(key: String): Boolean = write { it.putString(KEY_API_KEY, key.trim()) }

    override fun clearApiKey(): Boolean = write { it.remove(KEY_API_KEY) }

    /** Required key for document formatting, embedding, and knowledge-base search. */
    override fun getEmbeddingApiKey(): String? = read(KEY_EMBEDDING_API_KEY)

    override fun setEmbeddingApiKey(key: String): Boolean =
        write { it.putString(KEY_EMBEDDING_API_KEY, key.trim()) }

    override fun clearEmbeddingApiKey(): Boolean = write { it.remove(KEY_EMBEDDING_API_KEY) }

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
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_EMBEDDING_API_KEY = "gemini_embedding_api_key"
    }
}
