package com.assistant.app.network

import com.assistant.app.data.AzureConfig
import com.assistant.app.data.AzureMode
import com.assistant.app.data.azureBaseUrl
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

/**
 * Streaming text chat with an Azure OpenAI deployment through its OpenAI-compatible `/openai/v1` API.
 * Same contract as [GeminiChatClient]: it offers the given tools, runs them via `runTool` and feeds
 * each result back until the model answers. Images and PDFs are sent to the model as they are.
 */
class AzureChatClient(private val config: AzureConfig, private val defaultEndpoint: String) {

    companion object {
        private const val MAX_TEXT_FILE_CHARS = 100_000
        private const val MAX_TOOL_ROUNDS = 5
        private val FAILED_STATUSES = listOf("failed", "search_failed", "invalid", "unavailable", "timeout", "refused", "disabled", "permission_needed")
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val client = HttpClients.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val url: String
        get() {
            val endpoint = if (config.mode == AzureMode.ENDPOINT) config.endpoint else defaultEndpoint
            if (endpoint.isBlank()) throw java.io.IOException("Add the Azure OpenAI endpoint in Developer settings.")
            return azureBaseUrl(endpoint) + "/chat/completions"
        }

    suspend fun stream(
        systemInstruction: String,
        messages: List<ChatTurnMessage>,
        tools: JSONArray,
        runTool: suspend (name: String, args: JSONObject) -> String,
        listener: ChatStreamListener
    ): String = withContext(Dispatchers.IO) {
        val conversation = JSONArray().put(JSONObject().put("role", "system").put("content", systemInstruction))
        messages.forEach { conversation.put(messageJson(it)) }
        val openAiTools = toOpenAiTools(tools)

        val reply = StringBuilder()
        var toolIndex = 0
        for (round in 0..MAX_TOOL_ROUNDS) {
            val offerTools = openAiTools.length() > 0 && round < MAX_TOOL_ROUNDS
            val turn = streamOnce(conversation, if (offerTools) openAiTools else null, listener)
            reply.append(turn.text)
            if (turn.calls.isEmpty()) break

            val assistant = JSONObject().put("role", "assistant").put("content", turn.text.ifEmpty { JSONObject.NULL })
            assistant.put("tool_calls", JSONArray(turn.calls.map { call ->
                JSONObject().put("id", call.id).put("type", "function")
                    .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))
            }))
            conversation.put(assistant)
            for (call in turn.calls) {
                val index = toolIndex++
                listener.onToolStart(index, call.name)
                val args = runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                val result = runCatching { runTool(call.name, args) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrElse { "status=failed\nThat action could not be completed." }
                val failed = FAILED_STATUSES.any { result.startsWith("status=$it") }
                listener.onToolEnd(index, !failed)
                conversation.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", result))
            }
        }
        reply.toString()
    }

    private class ToolCall(val id: String, val name: String, val arguments: String)
    private class StreamTurn(val text: String, val calls: List<ToolCall>)

    private suspend fun streamOnce(conversation: JSONArray, tools: JSONArray?, listener: ChatStreamListener): StreamTurn {
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", conversation)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))
        if (tools != null) {
            body.put("tools", tools)
            // GPT-6 models reject function tools on this API unless reasoning is switched off.
            if (config.model.startsWith("gpt-6")) body.put("reasoning_effort", "none")
        }
        val request = Request.Builder()
            .url(url)
            .header("api-key", config.apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        val onCancel = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw GeminiEmbeddingClient.HttpFailure(
                        response.code, "Chat request failed (${response.code}): ${response.body?.string().orEmpty().take(200)}"
                    )
                }
                val source = response.body?.source() ?: throw java.io.IOException("Empty chat response")
                val text = StringBuilder()
                val ids = sortedMapOf<Int, String>()
                val names = sortedMapOf<Int, String>()
                val arguments = sortedMapOf<Int, StringBuilder>()
                var promptTokens = 0
                var answerTokens = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty() || chunk == "[DONE]") continue
                    val json = runCatching { JSONObject(chunk) }.getOrNull() ?: continue
                    json.optJSONObject("usage")?.let {
                        promptTokens = it.optInt("prompt_tokens", promptTokens)
                        answerTokens = it.optInt("completion_tokens", answerTokens)
                    }
                    val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    val piece = delta.stringOrEmpty("content")
                    if (piece.isNotEmpty()) {
                        text.append(piece)
                        listener.onText(piece)
                    }
                    val calls = delta.optJSONArray("tool_calls") ?: continue
                    for (i in 0 until calls.length()) {
                        val part = calls.optJSONObject(i) ?: continue
                        val index = part.optInt("index", i)
                        part.stringOrEmpty("id").takeIf { it.isNotEmpty() }?.let { ids[index] = it }
                        val function = part.optJSONObject("function") ?: continue
                        function.stringOrEmpty("name").takeIf { it.isNotEmpty() }?.let { names[index] = it }
                        arguments.getOrPut(index) { StringBuilder() }.append(function.stringOrEmpty("arguments"))
                    }
                }
                val finished = names.keys.map { index ->
                    ToolCall(ids[index] ?: "call_$index", names.getValue(index), arguments[index]?.toString().orEmpty())
                }
                if (promptTokens > 0 || answerTokens > 0) listener.onUsage(promptTokens, answerTokens)
                return StreamTurn(text.toString(), finished)
            }
        } finally {
            onCancel?.dispose()
        }
    }

    /** Azure sends `"content": null` in many chunks; org.json would turn that into the text "null". */
    private fun JSONObject.stringOrEmpty(name: String): String = if (isNull(name)) "" else optString(name)

    private fun messageJson(message: ChatTurnMessage): JSONObject {
        if (!message.fromUser) return JSONObject().put("role", "assistant").put("content", message.text)
        val parts = JSONArray()
        message.attachments.forEach { parts.put(attachmentPart(it)) }
        if (message.text.isNotBlank() || parts.length() == 0) parts.put(JSONObject().put("type", "text").put("text", message.text))
        return JSONObject().put("role", "user").put("content", parts)
    }

    private fun attachmentPart(file: ChatAttachment): JSONObject {
        fun dataUri() = "data:${file.mimeType};base64," + android.util.Base64.encodeToString(file.data, android.util.Base64.NO_WRAP)
        return when {
            file.isImage -> JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUri()))
            file.isPdf -> JSONObject().put("type", "file")
                .put("file", JSONObject().put("filename", file.name).put("file_data", dataUri()))
            file.isVideo -> JSONObject().put("type", "text")
                .put("text", "[Attached video: ${file.name}. The connected chat model cannot watch videos; say so if asked about it.]")
            file.isDocx -> JSONObject().put("type", "text")
                .put("text", "[Attached file: ${file.name}]\n" + com.assistant.app.data.DocxText.extract(file.data, MAX_TEXT_FILE_CHARS))
            else -> JSONObject().put("type", "text")
                .put("text", "[Attached file: ${file.name}]\n" + file.data.toString(Charsets.UTF_8).take(MAX_TEXT_FILE_CHARS))
        }
    }

    /** The app declares tools in Gemini's shape (upper-case types); OpenAI wants the same schema in lower case. */
    private fun toOpenAiTools(declarations: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until declarations.length()) {
            val declaration = declarations.getJSONObject(i)
            val function = JSONObject().put("name", declaration.getString("name"))
                .put("description", declaration.optString("description"))
            function.put(
                "parameters",
                declaration.optJSONObject("parameters")?.let { lowerCaseTypes(it) as JSONObject }
                    ?: JSONObject().put("type", "object").put("properties", JSONObject())
            )
            result.put(JSONObject().put("type", "function").put("function", function))
        }
        return result
    }

    private fun lowerCaseTypes(node: Any?): Any? = when (node) {
        is JSONObject -> JSONObject().also { copy ->
            node.keys().forEach { key ->
                val value = node.get(key)
                copy.put(key, if (key == "type" && value is String) value.lowercase() else lowerCaseTypes(value))
            }
        }
        is JSONArray -> JSONArray().also { copy -> for (i in 0 until node.length()) copy.put(lowerCaseTypes(node.get(i))) }
        else -> node
    }
}
