package com.assistant.app.network

import com.assistant.app.util.UserMessageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A file the user attached to a chat message; images and PDFs go to the model as they are, text files as text. */
class ChatAttachment(val name: String, val mimeType: String, val size: Int, dataProvider: () -> ByteArray) {
    constructor(name: String, mimeType: String, data: ByteArray) : this(name, mimeType, data.size, { data })

    /** The file's bytes; for a saved attachment they are read from storage only when first needed. */
    val data: ByteArray by lazy(dataProvider)
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isDocx: Boolean get() = mimeType == com.assistant.app.data.DocxText.MIME
}

/** One message of a typed chat, as sent to the model. */
data class ChatTurnMessage(val fromUser: Boolean, val text: String, val attachments: List<ChatAttachment> = emptyList())

/** Progress of one chat reply, called in order from the streaming coroutine. */
interface ChatStreamListener {
    suspend fun onText(delta: String)
    /** The model asked for a tool; [index] identifies this call in the reply. */
    suspend fun onToolStart(index: Int, name: String)
    suspend fun onToolEnd(index: Int, ok: Boolean)
    /** Tokens the model reported for one request of this reply; called once per request when the provider says. */
    suspend fun onUsage(inputTokens: Int, outputTokens: Int) {}
}

/**
 * Streaming text chat with a Gemini model (Server-Sent Events). It only offers the tools it is
 * given, and runs them through [stream]'s `runTool`, feeding each result back until the model answers.
 */
class GeminiChatClient(
    private val apiKey: String,
    private val model: String = CHAT_MODEL,
    /** Overridable so tests can point the client at a local server. */
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) {

    companion object {
        const val CHAT_MODEL = "gemini-3.5-flash-lite"
        private const val MAX_TEXT_FILE_CHARS = 100_000
        private const val MAX_TOOL_ROUNDS = 5
        private val FAILED_STATUSES = listOf("failed", "search_failed", "invalid", "unavailable", "timeout", "refused", "disabled", "permission_needed")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Gemini 3 models think before answering unless told otherwise, which made every chat reply wait several seconds
         * for its first word. A low thinking level keeps replies quick. A model that rejects the setting is asked again
         * without it, and that is remembered for the rest of the run.
         */
        private const val CHAT_THINKING_LEVEL = "low"
        @Volatile private var thinkingUnsupported = false

        internal fun resetThinkingSupportForTest() { thinkingUnsupported = false }
    }

    private val client = HttpClients.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Streams the reply to the last message in [messages]. [tools] are function declarations the model
     * may call; [runTool] runs one and returns its text result. Returns the full reply text.
     * Throws [GeminiEmbeddingClient.HttpFailure] for HTTP errors.
     */
    suspend fun stream(
        systemInstruction: String,
        messages: List<ChatTurnMessage>,
        tools: JSONArray,
        runTool: suspend (name: String, args: JSONObject) -> String,
        listener: ChatStreamListener
    ): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        messages.forEach { message ->
            val parts = JSONArray()
            message.attachments.forEach { parts.put(attachmentPart(it)) }
            if (message.text.isNotBlank() || parts.length() == 0) parts.put(JSONObject().put("text", message.text))
            contents.put(JSONObject().put("role", if (message.fromUser) "user" else "model").put("parts", parts))
        }

        val reply = StringBuilder()
        var toolIndex = 0
        var retriedMalformedCall = false
        var round = 0
        while (round <= MAX_TOOL_ROUNDS) {
            // After the last round the model must answer with what it has. The tools stay declared (the history holds
            // calls to them) but calling is switched off.
            val allowCalls = round < MAX_TOOL_ROUNDS
            val turn = try {
                streamOnce(systemInstruction, contents, tools.takeIf { it.length() > 0 }, allowCalls, roundSeparator(reply), listener)
            } catch (e: MalformedFunctionCall) {
                // The model sometimes writes a broken tool call; one fresh try nearly always comes back well formed.
                if (retriedMalformedCall) throw UserMessageException("The model could not use its tools for this message. Please try again.")
                retriedMalformedCall = true
                continue
            }
            round++
            reply.append(turn.text)
            if (turn.calls.isEmpty()) break

            // The model's own parts go back unchanged: Gemini 3 needs the thought signatures they carry.
            contents.put(JSONObject().put("role", "model").put("parts", turn.rawParts))
            val responses = JSONArray()
            for (call in turn.calls) {
                val index = toolIndex++
                listener.onToolStart(index, call.name)
                val result = runCatching { runTool(call.name, call.args) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrElse { "status=failed\nThat action could not be completed." }
                // "results" and "no_match" are normal knowledge-search outcomes; only real failures count as failed.
                val failed = FAILED_STATUSES.any { result.startsWith("status=$it") }
                listener.onToolEnd(index, !failed)
                val response = JSONObject().put("name", call.name).put("response", JSONObject().put("result", result))
                call.id?.let { response.put("id", it) }
                responses.put(JSONObject().put("functionResponse", response))
            }
            contents.put(JSONObject().put("role", "user").put("parts", responses))
        }
        reply.toString()
    }

    private class FunctionCall(val id: String?, val name: String, val args: JSONObject)
    private class MalformedFunctionCall : Exception()
    private class StreamTurn(val text: String, val calls: List<FunctionCall>, val rawParts: JSONArray)

    /**
     * [separator] goes before the first text of this request: a reply that continues after a tool call must not run on
     * from the words before it ("Let me checkIt is 30 degrees").
     */
    private suspend fun streamOnce(
        systemInstruction: String,
        contents: JSONArray,
        tools: JSONArray?,
        allowCalls: Boolean,
        separator: String,
        listener: ChatStreamListener
    ): StreamTurn {
        val withThinking = !thinkingUnsupported
        val generationConfig = JSONObject().put("maxOutputTokens", CHAT_REPLY_TOKEN_CAP)
        if (withThinking) generationConfig.put("thinkingConfig", JSONObject().put("thinkingLevel", CHAT_THINKING_LEVEL))
        val bodyJson = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            .put("generationConfig", generationConfig)
        if (tools != null) {
            bodyJson.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", ToolDeclarations.withoutPrivateFields(tools))))
            if (!allowCalls) bodyJson.put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "NONE")))
        }
        // Key goes in a header, not the URL, so it can't leak through logged URLs.
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        // Cancelling the coroutine (new chat, app closed) must also drop the open HTTP stream.
        val onCancel = watchCancellation(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty()
                    if (withThinking && response.code == 400 && detail.contains("thinking", ignoreCase = true)) {
                        thinkingUnsupported = true
                        return streamOnce(systemInstruction, contents, tools, allowCalls, separator, listener)
                    }
                    throw GeminiEmbeddingClient.HttpFailure(
                        response.code, "Chat request failed (${response.code}): ${detail.take(200)}"
                    )
                }
                val source = response.body?.source() ?: throw java.io.IOException("Empty chat response")
                val text = StringBuilder()
                val calls = mutableListOf<FunctionCall>()
                val raw = JSONArray()
                var promptTokens = 0
                var answerTokens = 0
                var finishReason: String? = null
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty()) continue
                    val json = runCatching { JSONObject(chunk) }.getOrNull() ?: continue
                    json.optJSONObject("error")?.let { throw UserMessageException(it.optString("message").ifBlank { "The model reported an error." }.take(160)) }
                    json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                        throw UserMessageException("This message was blocked by the model's safety filter ($it).")
                    }
                    json.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason")?.takeIf { it.isNotEmpty() }?.let { finishReason = it }
                    json.optJSONObject("usageMetadata")?.let {
                        promptTokens = it.optInt("promptTokenCount", promptTokens)
                        answerTokens = it.optInt("candidatesTokenCount") + it.optInt("thoughtsTokenCount")
                    }
                    val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val functionCall = part.optJSONObject("functionCall")
                        if (functionCall != null) {
                            calls += FunctionCall(
                                functionCall.optString("id").takeIf { it.isNotEmpty() },
                                functionCall.getString("name"),
                                functionCall.optJSONObject("args") ?: JSONObject()
                            )
                            raw.put(part)
                        } else if (!part.optBoolean("thought")) {
                            var piece = part.optString("text")
                            if (piece.isNotEmpty()) {
                                if (text.isEmpty()) piece = separator + piece
                                text.append(piece)
                                raw.put(part)
                                listener.onText(piece)
                            }
                        }
                    }
                }
                if (promptTokens > 0 || answerTokens > 0) listener.onUsage(promptTokens, answerTokens)
                // Only a clean STOP is a finished reply; anything else must not be saved as one.
                when (finishReason) {
                    "STOP" -> {}
                    null -> throw UserMessageException("The connection ended before the reply was finished. Please try again.")
                    "MAX_TOKENS" -> throw UserMessageException("The reply was cut off because it reached the length limit.")
                    "MALFORMED_FUNCTION_CALL", "UNEXPECTED_TOOL_CALL" -> if (text.isEmpty() && calls.isEmpty()) throw MalformedFunctionCall()
                        else throw UserMessageException("The reply stopped early ($finishReason).")
                    "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY" ->
                        throw UserMessageException("The reply was stopped by the model's safety filter.")
                    else -> throw UserMessageException("The reply stopped early ($finishReason).")
                }
                return StreamTurn(text.toString(), calls, raw)
            }
        } catch (e: java.io.IOException) {
            // A cancelled call fails with an IOException; report it as the cancellation it is.
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            onCancel.cancel()
        }
    }

    private fun attachmentPart(file: ChatAttachment): JSONObject =
        if (file.isDocx) {
            JSONObject().put("text", "[Attached file: ${file.name}]\n" + com.assistant.app.data.DocxText.extract(file.data, MAX_TEXT_FILE_CHARS))
        } else if (file.isImage || file.isPdf || file.isVideo) {
            JSONObject().put(
                "inline_data",
                JSONObject().put("mime_type", file.mimeType)
                    .put("data", android.util.Base64.encodeToString(file.data, android.util.Base64.NO_WRAP))
            )
        } else {
            JSONObject().put("text", "[Attached file: ${file.name}]\n" + file.data.toString(Charsets.UTF_8).take(MAX_TEXT_FILE_CHARS))
        }
}
