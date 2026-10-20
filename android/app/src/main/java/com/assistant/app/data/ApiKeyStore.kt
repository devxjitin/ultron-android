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

    /** Fine-grained, read-only token for the GitHub connector. */
    fun getGithubToken(): String? = read(KEY_GITHUB_TOKEN)

    fun setGithubToken(token: String): Boolean = write { it.putString(KEY_GITHUB_TOKEN, token.trim()) }

    fun clearGithubToken(): Boolean = write { it.remove(KEY_GITHUB_TOKEN) }

    /** Full URL of the user's YouTube MCP server; the secret access token is part of it. */
    fun getYoutubeUrl(): String? = read(KEY_YOUTUBE_URL)

    fun setYoutubeUrl(url: String): Boolean = write { it.putString(KEY_YOUTUBE_URL, url.trim()) }

    fun clearYoutubeUrl(): Boolean = write { it.remove(KEY_YOUTUBE_URL) }

    /** Azure OpenAI settings for the typed chat; the key is encrypted like the others. */
    fun getAzureConfig(): AzureConfig {
        val mode = runCatching { AzureMode.valueOf(read(KEY_AZURE_MODE).orEmpty()) }.getOrDefault(AzureMode.OFF)
        return AzureConfig(
            mode = mode,
            endpoint = read(KEY_AZURE_ENDPOINT).orEmpty(),
            apiKey = read(KEY_AZURE_KEY).orEmpty(),
            model = read(KEY_AZURE_MODEL) ?: DEFAULT_AZURE_MODEL
        )
    }

    fun setAzureConfig(config: AzureConfig): Boolean = write {
        it.putString(KEY_AZURE_MODE, config.mode.name)
            .putString(KEY_AZURE_ENDPOINT, config.endpoint.trim())
            .putString(KEY_AZURE_KEY, config.apiKey.trim())
            .putString(KEY_AZURE_MODEL, config.model.trim())
    }

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
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_YOUTUBE_URL = "youtube_mcp_url"
        const val KEY_AZURE_MODE = "azure_mode"
        const val KEY_AZURE_ENDPOINT = "azure_endpoint"
        const val KEY_AZURE_KEY = "azure_api_key"
        const val KEY_AZURE_MODEL = "azure_model"
    }
}
