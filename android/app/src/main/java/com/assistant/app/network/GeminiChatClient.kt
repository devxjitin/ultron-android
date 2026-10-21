package com.assistant.app.network

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
    private val model: String = CHAT_MODEL
) {

    companion object {
        const val CHAT_MODEL = "gemini-3.5-flash-lite"
        private const val MAX_TEXT_FILE_CHARS = 100_000
        private const val MAX_TOOL_ROUNDS = 5
        private val FAILED_STATUSES = listOf("failed", "search_failed", "invalid", "unavailable", "timeout", "refused", "disabled", "permission_needed")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
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
        for (round in 0..MAX_TOOL_ROUNDS) {
            // After the last round the model must answer with what it has, so tools are withheld.
            val offerTools = tools.length() > 0 && round < MAX_TOOL_ROUNDS
            val turn = streamOnce(systemInstruction, contents, if (offerTools) tools else null, listener)
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
    private class StreamTurn(val text: String, val calls: List<FunctionCall>, val rawParts: JSONArray)

    private suspend fun streamOnce(
        systemInstruction: String,
        contents: JSONArray,
        tools: JSONArray?,
        listener: ChatStreamListener
    ): StreamTurn {
        val bodyJson = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
        if (tools != null) bodyJson.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
        // Key goes in a header, not the URL, so it can't leak through logged URLs.
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        // Cancelling the coroutine (new chat, app closed) must also drop the open HTTP stream.
        val onCancel = watchCancellation(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw GeminiEmbeddingClient.HttpFailure(
                        response.code, "Chat request failed (${response.code}): ${response.body?.string().orEmpty().take(200)}"
                    )
                }
                val source = response.body?.source() ?: throw java.io.IOException("Empty chat response")
                val text = StringBuilder()
                val calls = mutableListOf<FunctionCall>()
                val raw = JSONArray()
                var promptTokens = 0
                var answerTokens = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty()) continue
                    val json = runCatching { JSONObject(chunk) }.getOrNull() ?: continue
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
                            val piece = part.optString("text")
                            if (piece.isNotEmpty()) {
                                text.append(piece)
                                raw.put(part)
                                listener.onText(piece)
                            }
                        }
                    }
                }
                if (promptTokens > 0 || answerTokens > 0) listener.onUsage(promptTokens, answerTokens)
                return StreamTurn(text.toString(), calls, raw)
            }
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
