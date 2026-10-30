package com.assistant.app.network

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.KnowledgeBaseProvider
import com.assistant.app.util.UserFacingError
import com.assistant.app.util.newLatencySession
import com.google.firebase.ai.type.ActivityDetectionConfig
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveGenerationConfig
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Live voice session through the Firebase AI Logic SDK. Same contract as [GeminiLiveClient]: mic audio goes in
 * through [sendAudioChunk], model audio comes out through the listener, which is always called on the main thread.
 * The SDK owns the protocol (setup, session resumption, sliding-window compression); audio capture and playback stay
 * with the app's own MicStreamer and AudioPlayer.
 */
@OptIn(PublicPreviewAPI::class)
class FirebaseLiveClient(private val model: String) : LiveClient {
    override val latency = newLatencySession()

    private companion object {
        const val TAG = "FirebaseLiveClient"
        const val SETUP_TIMEOUT_MS = 12_000L
        const val QUOTA_MESSAGE =
            "The Gemini quota for this project is used up. Check the plan in Firebase, or pick another model in Settings."
        const val MIC_BUFFER_CHUNKS = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val toolMutex = Mutex()
    // Mic chunks are sent one at a time in order; if the network stalls the oldest audio is dropped, never the newest.
    private val micChunks = Channel<ByteArray>(MIC_BUFFER_CHUNKS, BufferOverflow.DROP_OLDEST)

    @Volatile private var session: LiveSession? = null
    @Volatile private var listener: GeminiLiveClient.Listener? = null
    @Volatile private var closing = false
    private var closeNotified = false
    private var setupTimeoutRunnable: Runnable? = null
    private var connectJob: Job? = null
    private var sentAudioChunks = 0

    override fun connect(
        systemInstruction: String,
        settings: AssistantSettings,
        history: List<KnowledgeBaseProvider.HistoryTurn>,
        listener: GeminiLiveClient.Listener,
        resumeHandle: String?
    ) {
        this.listener = listener
        closing = false
        closeNotified = false
        latency.event("connect_start", mapOf("model" to model))
        Log.i(TAG, "connect() via Firebase (model=$model, voice=${settings.voiceName}, resume=${resumeHandle != null})")

        val timeout = Runnable {
            Log.e(TAG, "Setup timed out after ${SETUP_TIMEOUT_MS}ms")
            listener.onError("The assistant isn't available right now. Please try again in a moment.")
            close()
        }
        setupTimeoutRunnable = timeout
        mainHandler.postDelayed(timeout, SETUP_TIMEOUT_MS)

        connectJob = scope.launch {
            val live = try {
                val declarations = FirebaseGemini.functionDeclarations(liveFunctionDeclarations(settings))
                val generative = FirebaseGemini.ai().liveModel(
                    modelName = model,
                    generationConfig = liveConfig(settings),
                    tools = listOf(Tool.functionDeclarations(declarations)),
                    systemInstruction = Content(parts = listOf(TextPart(systemInstruction)))
                )
                // An empty resumption config asks the server for resumption handles; a handle continues that conversation.
                generative.connect(if (resumeHandle != null) SessionResumptionConfig(resumeHandle) else SessionResumptionConfig())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failConnect(e)
                return@launch
            }
            session = live
            latency.event("setup_ready")
            // History goes in before the view model starts streaming mic audio; a resumed session already holds it.
            if (resumeHandle == null) {
                history.forEach { turn ->
                    runCatching { live.send(Content(role = turn.role, parts = listOf(TextPart(turn.text))), false) }
                }
            }
            post {
                clearSetupTimeout()
                listener.onSetupComplete()
            }
            launch { pumpMic(live) }
            try {
                live.receive().collect { message -> handle(message) }
                notifyClosed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "receive ended: ${e.javaClass.simpleName}: ${e.message}")
                if (closing) notifyClosed() else failConnect(e) // quota and 403 stop the session instead of looping through reconnects
            }
        }
    }

    private fun liveConfig(settings: AssistantSettings): LiveGenerationConfig = LiveGenerationConfig.Builder().apply {
        responseModality = ResponseModality.AUDIO
        speechConfig = SpeechConfig(Voice(settings.voiceName), settings.languageCode)
        temperature = 1.0f
        // Both sides are transcribed so the app can show a transcript and keep turns as text.
        inputAudioTranscription = AudioTranscriptionConfig()
        outputAudioTranscription = AudioTranscriptionConfig()
        // Without compression the 128k-token context fills up and the server ends the session; the sliding window drops the oldest turns.
        contextWindowCompression = ContextWindowCompressionConfig(null, SlidingWindow())
        // Server VAD owns turn boundaries, including quiet one-word questions; 500 ms keeps natural pauses intact.
        realtimeInputConfig = RealtimeInputConfig.builder().apply {
            automaticActivityDetection = ActivityDetectionConfig.builder().apply {
                startSensitivity = ActivityDetectionConfig.Sensitivity.LOW
                endSensitivity = ActivityDetectionConfig.Sensitivity.HIGH
                prefixPaddingMs = 100
                silenceDurationMs = 500
            }.build()
        }.build()
    }.build()

    private suspend fun pumpMic(live: LiveSession) {
        for (chunk in micChunks) {
            try {
                live.sendAudioRealtime(InlineData(chunk, "audio/pcm;rate=${GeminiLiveClient.INPUT_SAMPLE_RATE_HZ}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "mic chunk not sent: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun handle(message: com.google.firebase.ai.type.LiveServerMessage) {
        val receivedAtMs = SystemClock.elapsedRealtime()
        when (message) {
            is LiveServerContent -> {
                val audio = message.content?.parts.orEmpty().filterIsInstance<InlineDataPart>().map { it.inlineData }
                audio.forEach { pcm -> if (pcm.isNotEmpty()) post { listener?.onAudioChunk(pcm, receivedAtMs) } }
                message.inputTranscription?.text?.takeIf { it.isNotEmpty() }?.let { post { listener?.onInputTranscript(it) } }
                message.outputTranscription?.text?.takeIf { it.isNotEmpty() }?.let { post { listener?.onOutputTranscript(it) } }
                if (message.interrupted) post { listener?.onInterrupted() }
                if (message.turnComplete) post { listener?.onTurnComplete() }
            }
            is LiveServerToolCall -> {
                val calls = message.functionCalls.mapIndexed { index, it ->
                    // Without an id every call would collapse into one in the view model, so make a unique one.
                    GeminiLiveClient.FunctionCall(it.id.orEmpty().ifEmpty { "call-${System.nanoTime()}-$index" }, it.name, FirebaseGemini.argsObject(it.args))
                }
                if (calls.isNotEmpty()) post { listener?.onToolCall(calls) }
            }
            is LiveServerToolCallCancellation -> {
                val ids = message.functionIds.filter { it.isNotBlank() }
                if (ids.isNotEmpty()) post { listener?.onToolCallCancellation(ids) }
            }
            is LiveSessionResumptionUpdate -> {
                val handle = message.newHandle.orEmpty()
                if (message.resumable == true && handle.isNotEmpty()) post { listener?.onResumptionHandle(handle) }
            }
            // The server announces an imminent disconnect; closing now sends the view model down its reconnect path
            // at once instead of streaming audio into a session that is about to end.
            is LiveServerGoAway -> {
                Log.i(TAG, "goAway received, closing to reconnect")
                closeSession()
            }
            else -> {}
        }
    }

    private fun failConnect(e: Exception) {
        Log.w(TAG, "connect failed: ${e.javaClass.simpleName}: ${e.message}")
        val quota = e is QuotaExceededException ||
            (e is ServiceConnectionHandshakeFailedException &&
                (e.message.orEmpty().contains("quota", true) || e.message.orEmpty().contains("billing", true)))
        post {
            clearSetupTimeout()
            val l = listener ?: return@post
            when {
                closing -> {}
                quota -> l.onError(QUOTA_MESSAGE)
                e is FirebaseAIException && forbidden(e) ->
                    l.onError(UserFacingError.forHttpFailure("Firebase Live connect", 403, e.message))
                // Dropped Wi-Fi, a busy server and the like: the view model's bounded reconnect handles it.
                else -> l.onClosed()
            }
        }
    }

    /** True when the failure maps to an HTTP 403, which retrying cannot fix. */
    private fun forbidden(e: FirebaseAIException): Boolean =
        (FirebaseGemini.asAppFailure(e) as? GeminiEmbeddingClient.HttpFailure)?.statusCode == 403

    private fun notifyClosed() {
        post {
            clearSetupTimeout()
            if (closeNotified || closing) return@post
            closeNotified = true
            listener?.onClosed()
        }
    }

    private fun closeSession() {
        val live = session ?: return
        CoroutineScope(Dispatchers.IO).launch { runCatching { live.close() } }
    }

    override fun sendAudioChunk(pcm16: ByteArray) {
        val accepted = micChunks.trySend(pcm16).isSuccess
        if (++sentAudioChunks % 25 == 0 || !accepted) Log.i(TAG, "mic chunks queued=$sentAudioChunks accepted=$accepted")
    }

    override fun sendToolResponses(results: List<GeminiLiveClient.ToolResult>): Boolean {
        val live = session ?: return false
        val responses = results.map { FunctionResponsePart(it.name, FirebaseGemini.resultObject(it.resultText), it.id) }
        scope.launch {
            toolMutex.withLock {
                try {
                    live.sendFunctionResponse(responses)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "tool response not sent: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        return true
    }

    override fun sendText(text: String): Boolean {
        val live = session ?: return false
        scope.launch {
            try {
                live.send(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "text not sent: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return true
    }

    override fun close() {
        closing = true
        latency.close("closed")
        clearSetupTimeout()
        micChunks.close()
        val live = session
        session = null
        scope.cancel()
        if (live != null) CoroutineScope(Dispatchers.IO).launch(NonCancellable) { runCatching { live.close() } }
    }

    // Every listener call lands on the main thread, serialized with the view model's own UI actions.
    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun clearSetupTimeout() {
        setupTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        setupTimeoutRunnable = null
    }
}
