package com.assistant.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The API-key storage the Developer Save needs. Writes report success so failures are never silent. */
interface ApiKeyStorage {
    fun getApiKey(): String?
    fun getEmbeddingApiKey(): String?
    fun setApiKey(key: String): Boolean
    fun clearApiKey(): Boolean
    fun setEmbeddingApiKey(key: String): Boolean
    fun clearEmbeddingApiKey(): Boolean
}

/** The Developer Save did not complete. [rollbackComplete] is false if restoring the old keys also failed. */
class DeveloperSaveFailedException(
    val rollbackComplete: Boolean,
    cause: Throwable
) : Exception(cause)

/**
 * Applies a Developer Save across the key store and the settings store. Only changed keys are
 * written (null = leave as is). If any step fails before settings are committed, the keys are
 * restored to their previous values, so a failed Save never leaves a mixed configuration behind.
 * This is best-effort across two stores, not an atomic transaction.
 */
suspend fun saveDeveloperConfiguration(
    storage: ApiKeyStorage,
    newApiKey: String?,
    newEmbeddingApiKey: String?,
    writeSettings: suspend () -> Unit
) {
    val previousApiKey = storage.getApiKey()
    val previousEmbeddingApiKey = storage.getEmbeddingApiKey()
    var apiKeyTouched = false
    var embeddingKeyTouched = false
    try {
        if (newApiKey != null) {
            apiKeyTouched = true
            check(storage.setApiKey(newApiKey)) { "Couldn't store the API key" }
        }
        if (newEmbeddingApiKey != null) {
            embeddingKeyTouched = true
            check(storage.setEmbeddingApiKey(newEmbeddingApiKey)) { "Couldn't store the knowledge API key" }
        }
        writeSettings()
    } catch (e: Throwable) {
        val rollbackComplete = withContext(NonCancellable) {
            var ok = true
            if (apiKeyTouched) {
                ok = runCatching {
                    if (previousApiKey == null) storage.clearApiKey() else storage.setApiKey(previousApiKey)
                }.getOrDefault(false) && ok
            }
            if (embeddingKeyTouched) {
                ok = runCatching {
                    if (previousEmbeddingApiKey == null) storage.clearEmbeddingApiKey()
                    else storage.setEmbeddingApiKey(previousEmbeddingApiKey)
                }.getOrDefault(false) && ok
            }
            ok
        }
        if (e is CancellationException) throw e
        throw DeveloperSaveFailedException(rollbackComplete, e)
    }
}
