package com.assistant.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.assistant.app.util.LatencyTool

/**
 * Calls Gemini's embedding endpoint(s) to turn text into vectors, used both when chunking a
 * document (task type RETRIEVAL_DOCUMENT) and when embedding a live query for search
 * (task type RETRIEVAL_QUERY). A reduced output dimensionality keeps the on-device stored
 * vectors small -- 768 is Google's documented "still strong quality" tier below the model's
 * native 3072.
 *
 * The app uses one fixed model, [MODEL], for both document and query embeddings. This keeps all
 * local vectors in one compatible space and makes retrieval behavior predictable.
 */
class GeminiEmbeddingClient(private val apiKey: String) {

    companion object {
        const val MODEL = "gemini-embedding-2"
        private const val OUTPUT_DIMENSIONS = 768
        private const val DOCUMENT_RETRY_COUNT = 4
        private const val DOCUMENT_RETRY_DELAY_MS = 5_000L
        private const val MAX_RETRY_AFTER_MS = 60_000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Transient transport/service failures are worth retrying; auth, validation and parse errors are not. */
        internal fun isRetryableEmbeddingFailure(e: Throwable): Boolean = when (e) {
            is HttpFailure -> e.statusCode == 408 || e.statusCode == 429 || e.statusCode in 500..599
            is java.io.IOException -> true
            else -> false
        }

        /** Honors a server-provided Retry-After (capped), otherwise the fixed default delay. */
        internal fun embeddingRetryDelayMs(e: Throwable): Long =
            ((e as? HttpFailure)?.retryAfterMs ?: DOCUMENT_RETRY_DELAY_MS).coerceIn(0L, MAX_RETRY_AFTER_MS)
    }

    private val client = HttpClients.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val queryClient = client.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    class HttpFailure(
        val statusCode: Int,
        message: String,
        /** Server-requested wait from the Retry-After header, if it sent one. */
        val retryAfterMs: Long? = null
    ) : java.io.IOException(message)

    /** A document chunk still could not be embedded after the bounded retry window. */
    class DocumentEmbeddingRetryExhausted(cause: Throwable) : java.io.IOException(cause)

    enum class TaskType(val apiValue: String) {
        DOCUMENT("RETRIEVAL_DOCUMENT"),
        QUERY("RETRIEVAL_QUERY")
    }

    /** The result of a successful embedding call, tagged with the app's fixed model. */
    data class Result(val model: String, val embedding: FloatArray)

    suspend fun embed(
        text: String, taskType: TaskType, trace: LatencyTool? = null
    ): Result {
        val startedAt = trace?.now()
        trace?.event("embedding_start", mapOf("model" to MODEL, "cache" to "miss"))
        var outcome = "failed"
        try {
            var retryCount = 0
            while (true) {
                try {
                    val result = Result(MODEL, embedOne(text, taskType))
                    outcome = "completed"
                    return result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Permanent failures (bad key, invalid request, unparseable response) fail at once.
                    if (taskType != TaskType.DOCUMENT || !isRetryableEmbeddingFailure(e)) throw e
                    if (retryCount >= DOCUMENT_RETRY_COUNT) throw DocumentEmbeddingRetryExhausted(e)
                    retryCount++
                    delay(embeddingRetryDelayMs(e))
                }
            }
        } finally {
            if (trace != null && startedAt != null) {
                trace.event("embedding_end", mapOf(
                    "model" to MODEL, "duration_ms" to (trace.now() - startedAt), "outcome" to outcome
                ))
            }
        }
    }

    private suspend fun embedOne(text: String, taskType: TaskType): FloatArray =
        withContext(Dispatchers.IO) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:embedContent"

            val body = JSONObject()
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
                .put("taskType", taskType.apiValue)
                .put("outputDimensionality", OUTPUT_DIMENSIONS)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)

            // Key goes in a header, not the URL, so it can't leak through logged URLs.
            val request = Request.Builder().url(url).header("x-goog-api-key", apiKey).post(body).build()

            val httpClient = if (taskType == TaskType.QUERY) queryClient else client
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfterMs = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                    throw HttpFailure(response.code, "Embedding request failed (${response.code}): ${raw.take(200)}", retryAfterMs)
                }
                val values = JSONObject(raw).getJSONObject("embedding").getJSONArray("values")
                FloatArray(values.length()) { i -> values.getDouble(i).toFloat() }
            }
        }

}
