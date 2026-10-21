package com.assistant.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit
import com.assistant.app.data.DEFAULT_DOCUMENT_FORMAT_PROMPT

/**
 * Plain (non-streaming) Gemini text generation, used to have the model draft an agent
 * persona/system prompt from the admin's knowledge documents -- a one-off text task, unrelated
 * to the Live voice session. Unlike embeddings, plain text output has no cross-model
 * compatibility issue, so [PRIMARY_MODEL] and [FALLBACK_MODEL] can be tried in plain sequence.
 */
class GeminiTextClient(
    private val apiKey: String,
    private val documentFormatPrompt: String = DEFAULT_DOCUMENT_FORMAT_PROMPT,
    private val documentFormatModel: String = DOCUMENT_FORMAT_MODEL,
    /** Called with the input and output tokens Gemini reports for each successful request. */
    private val onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null
) {

    companion object {
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.8-flash"
        const val DOCUMENT_FORMAT_MODEL = "gemini-3.8-flash"
        private const val DOCUMENT_OUTPUT_LIMIT_TOKENS = 65_536
        /** 4.2 seconds between successful calls stays below the 15-RPM quota. */
        private const val DOCUMENT_FORMAT_COOLDOWN_MS = 4_200L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val documentFormatMutex = Mutex()
        private var lastSuccessfulDocumentFormatAtMs = Long.MIN_VALUE
    }

    private val client = HttpClients.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Tries [PRIMARY_MODEL] first, [FALLBACK_MODEL] only if that call fails. Throws the
     *  primary call's exception if both fail. */
    suspend fun generateText(prompt: String): String {
        try {
            return generateWithModel(prompt, PRIMARY_MODEL)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (primaryError: Exception) {
            return try {
                generateWithModel(prompt, FALLBACK_MODEL)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw primaryError
            }
        }
    }

    /** Uses Gemini's native PDF understanding so the formatter sees visual layout, tables, and
     * images in addition to selectable text. */
    suspend fun formatKnowledgePdf(pdfBytes: ByteArray, retrievalChunkLimit: Int): String {
        val contents = JSONArray().put(
            JSONObject().put(
                "parts",
                JSONArray()
                    .put(JSONObject().put("text", "$documentFormatPrompt\n\nRetrieval configuration: The assistant retrieves only $retrievalChunkLimit chunks per query. Keep each topic self-contained whenever possible."))
                    .put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "application/pdf")
                                .put("data", Base64.getEncoder().encodeToString(pdfBytes))
                        )
                    )
            )
        )
        return documentFormatMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastSuccessfulDocumentFormatAtMs
            if (lastSuccessfulDocumentFormatAtMs != Long.MIN_VALUE && elapsed < DOCUMENT_FORMAT_COOLDOWN_MS) {
                delay(DOCUMENT_FORMAT_COOLDOWN_MS - elapsed)
            }
            val formatted = generateWithContents(
                contents = contents,
                model = documentFormatModel,
                maxOutputTokens = DOCUMENT_OUTPUT_LIMIT_TOKENS,
                rejectTruncatedOutput = true
            ).trim()
            lastSuccessfulDocumentFormatAtMs = System.currentTimeMillis()
            formatted
        }
    }

    private fun contentsFor(prompt: String): JSONArray = JSONArray().put(
        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
    )

    private suspend fun generateWithModel(
        prompt: String,
        model: String,
        maxOutputTokens: Int? = null,
        rejectTruncatedOutput: Boolean = false
    ): String = generateWithContents(
        contents = contentsFor(prompt),
        model = model,
        maxOutputTokens = maxOutputTokens,
        rejectTruncatedOutput = rejectTruncatedOutput
    )

    private suspend fun generateWithContents(
        contents: JSONArray,
        model: String,
        maxOutputTokens: Int? = null,
        rejectTruncatedOutput: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val body = JSONObject()
            .put("contents", contents)
            .apply { if (maxOutputTokens != null) put("generationConfig", JSONObject().put("maxOutputTokens", maxOutputTokens)) }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        // Key goes in a header, not the URL, so it can't leak through logged URLs.
        val request = Request.Builder().url(url).header("x-goog-api-key", apiKey).post(body).build()

        val call = client.newCall(request)
        // A timeout or cancel must also drop the open request, not wait for the blocking read to end.
        val watcher = watchCancellation(call)
        try { call.execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GeminiEmbeddingClient.HttpFailure(
                    response.code, "Text generation request failed (${response.code}): ${raw.take(200)}"
                )
            }
            val parsed = JSONObject(raw)
            parsed.optJSONObject("usageMetadata")?.let {
                onUsage?.invoke(it.optInt("promptTokenCount"), it.optInt("candidatesTokenCount") + it.optInt("thoughtsTokenCount"))
            }
            val candidate = parsed.getJSONArray("candidates").getJSONObject(0)
            if (rejectTruncatedOutput && candidate.optString("finishReason") == "MAX_TOKENS") {
                throw java.io.IOException("Document formatting exceeded the 65,536-output-token limit.")
            }
            candidate
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } } finally {
            watcher.cancel()
        }
    }
}
