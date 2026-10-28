package com.assistant.app.network

import com.assistant.app.util.UserMessageException
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionCallingConfig
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Streaming text chat with Gemini through the Firebase AI Logic SDK. Same contract as [GeminiChatClient] (tools run
 * through `runTool`, results fed back for up to five rounds), but the request and the Gemini key stay on Firebase's side.
 */
class FirebaseChatClient(private val model: String = GeminiChatClient.CHAT_MODEL) {

    companion object {
        private const val MAX_TEXT_FILE_CHARS = 100_000
        private const val MAX_TOOL_ROUNDS = 5
        private val FAILED_STATUSES = listOf("failed", "search_failed", "invalid", "unavailable", "timeout", "refused", "disabled", "permission_needed")

        /** Gemini 3 thinks before answering unless told otherwise; "low" keeps the first word quick. A model that rejects it is asked again without. */
        @Volatile private var thinkingUnsupported = false
    }

    suspend fun stream(
        systemInstruction: String,
        messages: List<ChatTurnMessage>,
        tools: JSONArray,
        runTool: suspend (name: String, args: JSONObject) -> String,
        listener: ChatStreamListener
    ): String = withContext(Dispatchers.IO) {
        val contents = mutableListOf<Content>()
        messages.forEach { message ->
            val parts = mutableListOf<Part>()
            message.attachments.forEach { parts += attachmentPart(it) }
            if (message.text.isNotBlank() || parts.isEmpty()) parts += TextPart(message.text)
            contents += Content(if (message.fromUser) "user" else "model", parts)
        }
        val declarations = if (tools.length() > 0) FirebaseGemini.functionDeclarations(ToolDeclarations.withoutPrivateFields(tools)) else emptyList()

        val declaredNames = (0 until tools.length()).mapNotNull { tools.optJSONObject(it)?.optString("name") }.toSet()

        val reply = StringBuilder()
        var toolIndex = 0
        var retriedMalformedCall = false
        var round = 0
        while (round <= MAX_TOOL_ROUNDS) {
            val allowCalls = round < MAX_TOOL_ROUNDS
            val turn = try {
                streamOnce(systemInstruction, contents, declarations, allowCalls, roundSeparator(reply), listener)
            } catch (e: MalformedFunctionCall) {
                if (retriedMalformedCall) throw UserMessageException("The model could not use its tools for this message. Please try again.")
                retriedMalformedCall = true
                continue
            }
            round++
            reply.append(turn.text)
            if (turn.calls.isEmpty()) break
            // Only tools this request declared may run, whatever name the model writes.
            if (turn.calls.any { it.name !in declaredNames }) {
                throw UserMessageException("The model asked for a tool that is not available. Please try again.")
            }

            // The model's own parts go back unchanged: Gemini 3 needs the thought signatures they carry.
            contents += Content("model", turn.rawParts)
            val responses = mutableListOf<Part>()
            for (call in turn.calls) {
                val index = toolIndex++
                listener.onToolStart(index, call.name)
                val result = runCatching { runTool(call.name, FirebaseGemini.argsObject(call.args)) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrElse { "status=failed\nThat action could not be completed." }
                val failed = FAILED_STATUSES.any { result.startsWith("status=$it") }
                listener.onToolEnd(index, !failed)
                responses += FunctionResponsePart(call.name, FirebaseGemini.resultObject(result), call.id)
            }
            contents += Content("user", responses)
        }
        reply.toString()
    }

    private class MalformedFunctionCall : Exception()
    private class StreamTurn(val text: String, val calls: List<FunctionCallPart>, val rawParts: List<Part>)

    private suspend fun streamOnce(
        systemInstruction: String,
        contents: List<Content>,
        declarations: List<com.google.firebase.ai.type.FunctionDeclaration>,
        allowCalls: Boolean,
        separator: String,
        listener: ChatStreamListener
    ): StreamTurn {
        val withThinking = !thinkingUnsupported
        val config = GenerationConfig.Builder().apply {
            maxOutputTokens = CHAT_REPLY_TOKEN_CAP
            if (withThinking) thinkingConfig = ThinkingConfig.Builder().apply { thinkingLevel = ThinkingLevel.LOW }.build()
        }.build()
        val generative = FirebaseGemini.ai().generativeModel(
            modelName = model,
            generationConfig = config,
            tools = if (declarations.isEmpty()) null else listOf(Tool.functionDeclarations(declarations)),
            toolConfig = if (declarations.isNotEmpty() && !allowCalls) ToolConfig(FunctionCallingConfig.none()) else null,
            systemInstruction = Content(parts = listOf(TextPart(systemInstruction)))
        )

        val text = StringBuilder()
        val calls = mutableListOf<FunctionCallPart>()
        val raw = mutableListOf<Part>()
        var promptTokens = 0
        var answerTokens = 0
        var finishReason: FinishReason? = null
        try {
            generative.generateContentStream(contents).collect { chunk ->
                currentCoroutineContext().ensureActive()
                chunk.usageMetadata?.let {
                    promptTokens = it.promptTokenCount
                    answerTokens = (it.candidatesTokenCount ?: 0) + it.thoughtsTokenCount
                }
                val candidate = chunk.candidates.firstOrNull() ?: return@collect
                candidate.finishReason?.let { finishReason = it }
                for (part in candidate.content.parts) {
                    when {
                        part is FunctionCallPart -> { calls += part; raw += part }
                        // A signature can arrive on a part with no text; the next request needs it back.
                        part is TextPart && !part.isThought && part.text.isEmpty() && part.thoughtSignature != null -> raw += part
                        part is TextPart && !part.isThought -> {
                            var piece = part.text
                            if (piece.isNotEmpty()) {
                                if (text.isEmpty()) piece = separator + piece
                                text.append(piece)
                                raw += part
                                listener.onText(piece)
                            }
                        }
                    }
                }
            }
        } catch (e: ResponseStoppedException) {
            // The SDK reports a non-STOP ending as an exception; the reason still decides what the user is told below.
            finishReason = e.response.candidates.firstOrNull()?.finishReason ?: FinishReason.OTHER
        } catch (e: FirebaseAIException) {
            if (withThinking && e.message.orEmpty().contains("thinking", ignoreCase = true)) {
                thinkingUnsupported = true
                return streamOnce(systemInstruction, contents, declarations, allowCalls, separator, listener)
            }
            throw FirebaseGemini.asAppFailure(e)
        }
        if (promptTokens > 0 || answerTokens > 0) listener.onUsage(promptTokens, answerTokens)
        // Only a clean STOP is a finished reply; anything else must not be saved as one.
        when (finishReason) {
            FinishReason.STOP -> {}
            null -> throw UserMessageException("The connection ended before the reply was finished. Please try again.")
            FinishReason.MAX_TOKENS -> throw UserMessageException("The reply was cut off because it reached the length limit.")
            FinishReason.MALFORMED_FUNCTION_CALL, FinishReason.UNEXPECTED_TOOL_CALL ->
                if (text.isEmpty() && calls.isEmpty()) throw MalformedFunctionCall()
                else throw UserMessageException("The reply stopped early (${finishReason?.name}).")
            FinishReason.SAFETY, FinishReason.RECITATION, FinishReason.BLOCKLIST, FinishReason.PROHIBITED_CONTENT,
            FinishReason.SPII, FinishReason.IMAGE_SAFETY -> throw UserMessageException("The reply was stopped by the model's safety filter.")
            else -> throw UserMessageException("The reply stopped early (${finishReason?.name}).")
        }
        return StreamTurn(text.toString(), calls, raw)
    }

    private fun attachmentPart(file: ChatAttachment): Part =
        if (file.isDocx) {
            TextPart("[Attached file: ${file.name}]\n" + com.assistant.app.data.DocxText.extract(file.data, MAX_TEXT_FILE_CHARS))
        } else if (file.isImage || file.isPdf || file.isVideo) {
            InlineDataPart(file.data, file.mimeType)
        } else {
            TextPart("[Attached file: ${file.name}]\n" + file.data.toString(Charsets.UTF_8).take(MAX_TEXT_FILE_CHARS))
        }
}
