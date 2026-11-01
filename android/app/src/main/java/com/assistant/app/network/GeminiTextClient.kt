package com.assistant.app.network

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.TextPart

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
    private val onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null,
    /** True sends requests through the Firebase AI Logic SDK and ignores [apiKey]. */
    private val useFirebase: Boolean = false
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

    /** Describes a picture (for example a screenshot) following [prompt]. */
    suspend fun describeImage(jpeg: ByteArray, prompt: String): String {
        val contents = JSONArray().put(
            JSONObject().put(
                "parts",
                JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", "image/jpeg").put("data", Base64.getEncoder().encodeToString(jpeg))
                        )
                    )
            )
        )
        return try {
            generateWithContents(contents, PRIMARY_MODEL, maxOutputTokens = 4_096)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (primaryError: Exception) {
            try {
                generateWithContents(contents, FALLBACK_MODEL, maxOutputTokens = 4_096)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw primaryError
            }
        }
    }

    /** The same request through the Firebase AI Logic SDK; [contents] holds only text and inline_data parts. */
    private suspend fun generateViaFirebase(
        contents: JSONArray,
        model: String,
        maxOutputTokens: Int?,
        rejectTruncatedOutput: Boolean
    ): String {
        val sdkContents = (0 until contents.length()).map { index ->
            val parts = contents.getJSONObject(index).getJSONArray("parts")
            Content(
                parts = (0 until parts.length()).map { p ->
                    val part = parts.getJSONObject(p)
                    val inline = part.optJSONObject("inline_data")
                    if (inline != null) InlineDataPart(Base64.getDecoder().decode(inline.getString("data")), inline.getString("mime_type"))
                    else TextPart(part.getString("text"))
                }
            )
        }
        val config = GenerationConfig.Builder().apply { this.maxOutputTokens = maxOutputTokens }.build()
        val generative = FirebaseGemini.ai().generativeModel(modelName = model, generationConfig = config)
        val response = try {
            generative.generateContent(sdkContents)
        } catch (e: ResponseStoppedException) {
            if (e.response.candidates.firstOrNull()?.finishReason == FinishReason.MAX_TOKENS && rejectTruncatedOutput) {
                throw java.io.IOException("Document formatting exceeded the 65,536-output-token limit.")
            }
            throw FirebaseGemini.asAppFailure(e)
        } catch (e: FirebaseAIException) {
            throw FirebaseGemini.asAppFailure(e)
        }
        response.usageMetadata?.let { onUsage?.invoke(it.promptTokenCount, (it.candidatesTokenCount ?: 0) + it.thoughtsTokenCount) }
        if (rejectTruncatedOutput && response.candidates.firstOrNull()?.finishReason == FinishReason.MAX_TOKENS) {
            throw java.io.IOException("Document formatting exceeded the 65,536-output-token limit.")
        }
        return response.text ?: throw java.io.IOException("The model returned no text.")
    }

    private suspend fun generateWithContents(
        contents: JSONArray,
        model: String,
        maxOutputTokens: Int? = null,
        rejectTruncatedOutput: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (useFirebase) return@withContext generateViaFirebase(contents, model, maxOutputTokens, rejectTruncatedOutput)
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
        } } catch (e: java.io.IOException) {
            // A cancelled call fails with an IOException; report it as the cancellation it is.
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            watcher.cancel()
        }
    }
}
