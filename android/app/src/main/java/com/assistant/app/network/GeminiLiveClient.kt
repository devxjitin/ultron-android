package com.assistant.app.network

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.assistant.app.BuildConfig
import com.assistant.app.data.AssistantOptions
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.KnowledgeBaseProvider
import com.assistant.app.data.GITHUB_TOOL_PREFIX
import com.assistant.app.data.YOUTUBE_TOOL_PREFIX
import com.assistant.app.data.enabledGoogleTools
import com.assistant.app.data.MEMORY_FORGET_TOOL
import com.assistant.app.data.MEMORY_SAVE_TOOL
import com.assistant.app.util.UserFacingError
import com.assistant.app.util.newLatencySession
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for the Gemini Live bidirectional-streaming WebSocket API.
 *
 * This talks to the Live endpoint directly from the tablet using an on-device API key.
 * That is the tradeoff we accepted for a fast, backend-free build: anyone who decompiles
 * the app can recover the key. Acceptable for an internal kiosk prototype; if this
 * ever ships beyond that, move this connection behind a small server-side proxy instead.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val model: String
) {
    val latency = newLatencySession()

    interface Listener {
        fun onSetupComplete() {}
        /** Raw PCM16 little-endian mono audio at 24kHz, ready to hand to AudioTrack. */
        fun onAudioChunk(pcm: ByteArray, receivedAtMs: Long) {}
        fun onInputTranscript(text: String) {}
        fun onOutputTranscript(text: String) {}
        fun onTurnComplete() {}
        fun onInterrupted() {}
        /** The model wants to call one or more functions before continuing this turn -- it will
         *  not speak again until a matching [sendToolResponse] is sent for every call id here. */
        fun onToolCall(calls: List<FunctionCall>) {}
        /** The server abandoned the listed calls, normally because the visitor interrupted the
         *  model while a local tool was still running. Their results must not be sent late. */
        fun onToolCallCancellation(ids: List<String>) {}
        fun onError(message: String) {}
        fun onClosed() {}
        /** The latest handle that lets a dropped connection continue this conversation; see
         *  [connect]'s resumeHandle. */
        fun onResumptionHandle(handle: String) {}
    }

    /** One function-call request from the model, as received in a `toolCall` message. */
    data class FunctionCall(val id: String, val name: String, val args: JSONObject)

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val ENDPOINT =
            "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val INPUT_SAMPLE_RATE_HZ = 16_000
        const val OUTPUT_SAMPLE_RATE_HZ = 24_000
        private const val SETUP_TIMEOUT_MS = 12_000L
        private const val QUOTA_MESSAGE =
            "The Gemini quota for this API key is used up. Check the plan in Google AI Studio, or pick another model in Settings."

    }

    // No pingInterval: Google's Live endpoint doesn't answer OkHttp's low-level WebSocket
    // keepalive pings, only real data frames -- setting one made every session die with a
    // "didn't receive pong" failure even though the actual protocol exchange works fine.
    // Continuous mic-audio streaming keeps the connection warm on its own.
    private val client = HttpClients.shared.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming connection stays open
        .build()

    private val extendedThinking =
        AssistantOptions.liveModels.firstOrNull { it.id == model }?.extendedThinking == true

    @Volatile private var quotaReported = false

    private fun isQuotaClose(reason: String) =
        reason.contains("quota", ignoreCase = true) || reason.contains("billing", ignoreCase = true)

    private var webSocket: WebSocket? = null
    private var listener: Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var setupTimeoutRunnable: Runnable? = null

    // Coarse latency instrumentation -- logged, not surfaced in UI. Useful for comparing
    // connect-to-ready timing across changes (e.g. confirming the RAG removal actually helped).
    private var connectStartMillis = 0L
    private var firstAudioChunkLogged = false
    private var sentAudioChunks = 0

    /** Prior turns to seed once setup completes; see [sendHistory]. */
    private var pendingHistory: List<KnowledgeBaseProvider.HistoryTurn> = emptyList()

    /** [resumeHandle] continues an earlier connection's conversation server-side, so [history] is
     *  not re-sent (it would duplicate what the server already holds). */
    fun connect(
        systemInstruction: String,
        settings: AssistantSettings,
        history: List<KnowledgeBaseProvider.HistoryTurn>,
        listener: Listener,
        resumeHandle: String? = null
    ) {
        this.listener = listener
        pendingHistory = if (resumeHandle != null) emptyList() else history
        connectStartMillis = System.currentTimeMillis()
        quotaReported = false
        firstAudioChunkLogged = false
        sentAudioChunks = 0
        latency.event("connect_start", mapOf("model" to model))
        val url = "wss://$HOST/ws/$ENDPOINT?key=$apiKey"
        val request = Request.Builder().url(url).build()
        Log.i(TAG, "connect() opening wss://$HOST/ws/$ENDPOINT?key=*** (model=$model, voice=${settings.voiceName})")

        val timeoutRunnable = Runnable {
            Log.e(TAG, "Setup timed out after ${SETUP_TIMEOUT_MS}ms -- no setupComplete or error received")
            listener.onError("The assistant isn't available right now. Please try again in a moment.")
            webSocket?.cancel()
        }
        setupTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, SETUP_TIMEOUT_MS)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "onOpen: handshake complete, sending setup message")
                webSocket.send(buildSetupMessage(systemInstruction, settings, resumeHandle).toString())
            }

            // OkHttp delivers every WebSocketListener callback on its own internal dispatcher
            // thread, never the main thread. The Listener we hand these to (the ViewModel) reads
            // and mutates plain fields (liveClient, warmClient, micStreamer, transcript buffers,
            // timeout jobs) with no synchronization of its own, on the assumption that it's only
            // ever touched from the main thread alongside user-triggered actions (tapping Stop,
            // Settings, etc). Posting through mainHandler here is what makes that assumption
            // true -- every Listener call happens on the main thread, serialized with everything
            // else the ViewModel does, so a callback can never interleave with (and race) a
            // concurrent UI action the way it could when both ran on different threads.
            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatchServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                dispatchServerMessage(bytes.utf8())
            }

            // The server sent a close frame. OkHttp only completes the close handshake (and only
            // then fires onClosed) once we close our side too -- without this, a server-initiated
            // close (session limit, rejected setup) never reached the ViewModel's reconnect/error
            // path and the UI sat on "Listening..." until the idle timeout.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "onClosing: code=$code reason=$reason")
                webSocket.close(1000, null)
                // A spent quota will not fix itself by reconnecting, so say so instead of looping.
                if (isQuotaClose(reason)) {
                    quotaReported = true
                    mainHandler.post {
                        clearSetupTimeout()
                        listener.onError(QUOTA_MESSAGE)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    clearSetupTimeout()
                    val httpCode = response?.code
                    if (httpCode == 400 || httpCode == 401 || httpCode == 403 || httpCode == 404) {
                        // Invalid/revoked API key or bad request/model -- retrying won't help, surface it directly
                        // instead of burning reconnect attempts on a request that can never
                        // succeed.
                        listener.onError(UserFacingError.forHttpFailure("WebSocket failure", httpCode, response?.message))
                    } else {
                        // Transport-level failure (dropped Wi-Fi, DNS hiccup, timeout, reset,
                        // etc.) -- route through the same path as a server-initiated close so it
                        // gets the existing bounded reconnect-with-backoff logic instead of
                        // dead-ending on an error the visitor has to manually retry.
                        Log.w(TAG, "onFailure treated as retryable (httpCode=$httpCode): ${t.javaClass.simpleName}: ${t.message}")
                        listener.onClosed()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "onClosed: code=$code reason=$reason")
                if (quotaReported) return
                mainHandler.post {
                    clearSetupTimeout()
                    listener.onClosed()
                }
            }
        })
    }

    /** Seeds earlier turns as role-separated client content ("user" / "model") instead of mixing
     *  them into the system instruction. turnComplete=false adds context without asking the
     *  model to respond. Sent once, before any realtime audio; no-op when there is no history. */
    private fun sendHistory() {
        val history = pendingHistory
        pendingHistory = emptyList()
        if (history.isEmpty()) return
        val turns = JSONArray()
        history.forEach { turn ->
            turns.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }
        val message = JSONObject().put(
            "clientContent",
            JSONObject().put("turns", turns).put("turnComplete", false)
        )
        val accepted = webSocket?.send(message.toString()) == true
        Log.i(TAG, "history seeded turns=${history.size} accepted=$accepted")
    }

    private fun clearSetupTimeout() {
        setupTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        setupTimeoutRunnable = null
    }

    private fun buildSetupMessage(
        systemInstruction: String,
        settings: AssistantSettings,
        resumeHandle: String?
    ): JSONObject {
        val voiceConfig = JSONObject().put(
            "prebuiltVoiceConfig",
            JSONObject().put("voiceName", settings.voiceName)
        )
        val speechConfig = JSONObject()
            .put("voiceConfig", voiceConfig)
            .put("languageCode", settings.languageCode)

        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", speechConfig)
            .put("temperature", 1.0)
        if (extendedThinking) {
            generationConfig.put(
                "thinkingConfig",
                JSONObject().put("thinkingLevel", AssistantOptions.EXTENDED_THINKING_LEVEL)
            )
        }

        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", generationConfig)
            // Let server VAD own turn boundaries, including quiet one-word questions.
            // 500ms keeps natural pauses intact while reducing endpointing delay.
            .put("realtimeInputConfig", JSONObject()
                .put("automaticActivityDetection",
                JSONObject()
                    .put("disabled", false)
                    .put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                    .put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
                    .put("prefixPaddingMs", 100)
                    .put("silenceDurationMs", 500)
            ))
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            // Ask the server to transcribe both sides so we can show a transcript
            // and persist turns as text, even though the exchange itself is audio.
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("tools", JSONArray().put(buildToolsDeclaration(settings)))
            // Without compression the 128k-token context (audio + every knowledge-search result)
            // fills up and the server ends the session; the sliding window drops the oldest turns.
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            // Makes the server send resumption handles; a reconnect passes the latest one back.
            .put("sessionResumption", JSONObject().apply { if (resumeHandle != null) put("handle", resumeHandle) })

        return JSONObject().put("setup", setup)
    }

    /** Knowledge search is always declared; each device tool is added only if its switch is on. */
    private fun buildToolsDeclaration(settings: AssistantSettings): JSONObject {
        val declarations = JSONArray()
        searchKnowledgeBaseTool().optJSONArray("functionDeclarations")?.let { declarations.put(it.get(0)) }
        if (settings.memoryEnabled) ToolDeclarations.memory(extendedThinking).forEach { declarations.put(it) }
        DeviceTool.entries.filter { it in settings.enabledDeviceTools }
            .forEach { declarations.put(ToolDeclarations.device(it, extendedThinking)) }
        if (settings.githubLogin.isNotEmpty()) {
            settings.githubTools.filter { it.name in settings.enabledGithubTools }.forEach { declarations.put(ToolDeclarations.mcp(it, extendedThinking)) }
        }
        settings.enabledGoogleTools().forEach { (service, tool) ->
            declarations.put(ToolDeclarations.mcp(tool, extendedThinking, service.toolPrefix))
        }
        if (settings.youtubeHost.isNotEmpty()) {
            settings.youtubeTools.filter { it.name in settings.enabledYoutubeTools }
                .forEach { declarations.put(ToolDeclarations.mcp(it, extendedThinking, YOUTUBE_TOOL_PREFIX)) }
        }
        return JSONObject().put("functionDeclarations", declarations)
    }

    /** The knowledge tool: semantic search over the admin's user-managed knowledge
     *  documents. See RagSearchEngine/RagDocumentStore for the actual retrieval. */
    private fun searchKnowledgeBaseTool(): JSONObject {
        val parameters = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject().put(
                    "query",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "The user's question, in their own words.")
                )
            )
            .put("required", JSONArray().put("query"))

        val functionDeclaration = JSONObject()
            .put("name", "search_knowledge_base")
            .put(
                "description",
                "Searches the documents and notes the user saved in this app. Call this only " +
                    "when the user asks about information they stored themselves, never for " +
                    "everyday or general questions."
            )
            .put("parameters", parameters)
        if (extendedThinking) functionDeclaration.put("behavior", "NON_BLOCKING")

        return JSONObject().put("functionDeclarations", JSONArray().put(functionDeclaration))
    }

    /** Streams a chunk of mic audio: PCM16 little-endian, mono, 16kHz. */
    fun sendAudioChunk(pcm16: ByteArray) {
        val audio = JSONObject()
            .put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE_HZ")
            .put("data", Base64.encodeToString(pcm16, Base64.NO_WRAP))

        // "mediaChunks" is deprecated on this API version -- the server rejects it outright
        // with a close frame ("realtime_input.media_chunks is deprecated. Use audio, video,
        // or text instead."). Confirmed against the live endpoint that "audio" is correct.
        val message = JSONObject().put("realtimeInput", JSONObject().put("audio", audio))
        val accepted = webSocket?.send(message.toString()) == true
        if (++sentAudioChunks % 25 == 0 || !accepted) {
            Log.i(TAG, "mic chunks sent=$sentAudioChunks accepted=$accepted")
        }
    }

    /** Answers a [FunctionCall] the model asked for -- must be sent for every call id in a
     *  `toolCall` message before the model will speak again this turn. */
    fun sendToolResponse(id: String, name: String, resultText: String): Boolean {
        val functionResponse = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("response", JSONObject().put("result", resultText))

        val message = JSONObject().put(
            "toolResponse",
            JSONObject().put("functionResponses", JSONArray().put(functionResponse))
        )
        return webSocket?.send(message.toString()) == true
    }

    fun close() {
        latency.close("closed")
        clearSetupTimeout()
        webSocket?.close(1000, "client closing")
        webSocket = null
    }

    /** Parses on the calling (OkHttp) thread -- JSON and Base64 decoding of audio is too heavy for
     *  the main thread -- and posts only the resulting listener calls to the main thread, in
     *  order, so the ViewModel still sees every callback serialized with its UI actions. */
    private fun dispatchServerMessage(raw: String) {
        val receivedAtMs = SystemClock.elapsedRealtime()
        val actions = parseServerMessage(raw, receivedAtMs)
        if (actions.isEmpty()) return
        mainHandler.post { actions.forEach { it() } }
    }

    /** Extended-thinking models send `turnComplete` after a filler utterance while still working
     *  (`interactionStatus` IN_PROGRESS, in serverContent or at the top level); that is not the
     *  end of the visitor's turn, so it must not reach the ViewModel as one. */
    private fun isStillWorking(node: JSONObject): Boolean =
        node.optString("interactionStatus") == "IN_PROGRESS"

    private fun parseServerMessage(raw: String, receivedAtMs: Long): List<() -> Unit> {
        val actions = mutableListOf<() -> Unit>()
        var turnComplete = false
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Non-JSON message ignored (${raw.length} chars)")
            return actions
        }

        if (BuildConfig.DEBUG && !json.has("setupComplete")) {
            val content = json.optJSONObject("serverContent")
            Log.i(TAG, "server event: content=${content?.keys()?.asSequence()?.toList()} " +
                "toolCall=${json.has("toolCall")} error=${json.has("error")}")
        }

        if (json.has("setupComplete")) {
            latency.event("setup_ready", at = receivedAtMs)
            Log.i(TAG, "setupComplete received (${System.currentTimeMillis() - connectStartMillis}ms after connect())")
            actions.add {
                clearSetupTimeout()
                // Must go out before the ViewModel starts streaming mic audio.
                sendHistory()
                listener?.onSetupComplete()
            }
            return actions
        }

        json.optJSONObject("serverContent")?.let { serverContent ->
            serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    part.optJSONObject("inlineData")?.optString("data")?.let { b64 ->
                        if (b64.isNotEmpty()) {
                            if (!firstAudioChunkLogged) {
                                firstAudioChunkLogged = true
                                Log.i(TAG, "first audio chunk (${System.currentTimeMillis() - connectStartMillis}ms after connect())")
                            }
                            val pcm = Base64.decode(b64, Base64.NO_WRAP)
                            actions.add { listener?.onAudioChunk(pcm, receivedAtMs) }
                        }
                    }
                }
            }
            serverContent.optJSONObject("inputTranscription")?.optString("text")?.let {
                if (it.isNotEmpty()) actions.add { listener?.onInputTranscript(it) }
            }
            serverContent.optJSONObject("outputTranscription")?.optString("text")?.let {
                if (it.isNotEmpty()) actions.add { listener?.onOutputTranscript(it) }
            }
            if (serverContent.optBoolean("interrupted")) actions.add { listener?.onInterrupted() }
            // Defer this until after toolCall below. Although these normally arrive as separate
            // frames, a combined frame must preserve the fact that a tool continuation is still
            // pending when the ViewModel sees the boundary.
            turnComplete = serverContent.optBoolean("turnComplete")
            if (isStillWorking(serverContent)) turnComplete = false
        }

        json.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { functionCalls ->
            val calls = (0 until functionCalls.length()).map { i ->
                val call = functionCalls.getJSONObject(i)
                FunctionCall(
                    id = call.getString("id"),
                    name = call.getString("name"),
                    args = call.optJSONObject("args") ?: JSONObject()
                )
            }
            if (calls.isNotEmpty()) actions.add { listener?.onToolCall(calls) }
        }

        json.optJSONObject("toolCallCancellation")?.optJSONArray("ids")?.let { ids ->
            val cancelled = (0 until ids.length()).mapNotNull { index ->
                ids.optString(index).takeIf { it.isNotBlank() }
            }
            if (cancelled.isNotEmpty()) actions.add { listener?.onToolCallCancellation(cancelled) }
        }

        if (turnComplete && !isStillWorking(json)) actions.add { listener?.onTurnComplete() }

        json.optJSONObject("error")?.let {
            val errorText = it.optString("message", "Unknown error")
            val message = UserFacingError.forServerErrorMessage("Gemini Live server error", errorText)
            actions.add { listener?.onError(message) }
        }

        // The server announces an imminent disconnect (session/time limit) via goAway instead of
        // just closing outright -- previously unhandled, so the client kept streaming audio into
        // a session the server had already given up on, with every subsequent turn silently
        // burning the full TURN_RESPONSE_TIMEOUT_MS for a reply that was never coming. Closing
        // now instead of waiting routes this through the existing onClosed()/reconnect-with-
        // backoff path immediately rather than after a wasted 15s timeout.
        json.optJSONObject("goAway")?.let {
            Log.i(TAG, "goAway received (timeLeft=${it.optString("timeLeft")}) -- closing to reconnect")
            actions.add { webSocket?.close(1000, "server goAway") }
        }

        json.optJSONObject("sessionResumptionUpdate")?.let {
            val handle = it.optString("newHandle")
            val resumable = it.optBoolean("resumable")
            Log.i(TAG, "sessionResumptionUpdate received (resumable=$resumable)")
            if (resumable && handle.isNotEmpty()) actions.add { listener?.onResumptionHandle(handle) }
        }
        return actions
    }
}
