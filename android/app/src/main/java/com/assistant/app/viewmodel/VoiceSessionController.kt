package com.assistant.app.viewmodel

import android.app.Application
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import com.assistant.app.AppForegroundService
import com.assistant.app.BuildConfig
import com.assistant.app.audio.AudioPlayer
import com.assistant.app.audio.MicStreamer
import com.assistant.app.audio.visualLevel
import com.assistant.app.data.AccessGate
import com.assistant.app.data.AppSettingsStore
import com.assistant.app.data.AssistantOptions
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.ConversationHistoryStore
import com.assistant.app.data.ConversationStore
import com.assistant.app.data.ConversationTurn
import com.assistant.app.data.KnowledgeBaseProvider
import com.assistant.app.data.MemoryStore
import com.assistant.app.data.MicSensitivity
import com.assistant.app.data.UsageDay
import com.assistant.app.data.memoriesForPrompt
import com.assistant.app.network.GeminiLiveClient
import com.assistant.app.network.LiveClient
import com.assistant.app.util.LatencyTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A voice session is running, connecting or starting up (not idle and not in an error). */
internal fun isSessionActive(state: VoiceState) = state != VoiceState.IDLE && state != VoiceState.ERROR

/**
 * One voice session at a time: opening it (cold, or promoted from a warm one opened ahead of time), streaming the
 * mic and playing the reply, reconnecting when the server closes it, and ending it. [AssistantViewModel] decides
 * when to start or stop; this class holds the live connection, the mic, the player and everything that goes with them.
 */
internal class VoiceSessionController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<AssistantUiState>,
    private val voiceLevel: MutableStateFlow<Float>,
    audioManager: AudioManager,
    private val settingsStore: AppSettingsStore,
    private val conversationStore: ConversationStore,
    private val memoryStore: MemoryStore,
    historyStoreProvider: () -> ConversationHistoryStore,
    private val resolveApiKey: () -> String,
    private val hasGeminiAccess: () -> Boolean,
    /** Runs one tool the model called; see [ToolRunner]. */
    execute: suspend (GeminiLiveClient.FunctionCall, LatencyTool?) -> String,
    private val showToast: (String, AppNotificationType) -> Unit,
    private val recordUsage: (UsageDay) -> Unit,
    private val recordVoiceUsage: () -> Unit,
    private val markVoiceUsageStart: () -> Unit,
    private val openDeveloperPage: () -> Unit,
    private val indexConversationHistory: () -> Unit
) {
    private companion object {
        const val WARM_SESSION_TTL_MS = 4 * 60 * 1000L
        /** How long the app must stay in front, and settings stay unchanged, before a warm session is opened. */
        const val PREWARM_DELAY_MS = 2_000L
        /** After a server go-away notice: how long the current reply may finish first, and the margin kept before the deadline. */
        const val GO_AWAY_DEFAULT_WAIT_MS = 8_000L
        const val GO_AWAY_MARGIN_MS = 2_000L

    }

    /** Every finished exchange, kept for long-term memory. Opened on first use, off the main thread. */
    private val conversationHistoryStore by lazy(historyStoreProvider)

    private var reconnectJob: Job? = null
    private var sessionStartJob: Job? = null
    /** Latest server handle for the active conversation, used only to resume it after an
     *  automatic reconnect; cleared whenever the user starts or stops a session. */
    private var sessionResumptionHandle: String? = null
    private var prewarmJob: Job? = null
    /** A server "going away" notice that waits for the current reply to finish before reconnecting. */
    private var pendingGoAway: (() -> Unit)? = null
    private var goAwayJob: Job? = null
    var isForeground = false
    /** True while a session keeps running after the app left the screen. */
    var backgroundSessionAllowed = false

    /** Groups the exchanges of one mic session; a reconnect inside the session keeps it. */
    var historySessionId = java.util.UUID.randomUUID().toString()

    /** True while a phone call rings or runs: Ultron stays connected but silent, and picks up again afterwards. */
    var pausedByCall = false
        private set
    private var callWatchJob: Job? = null

    var liveClient: LiveClient? = null
        private set
    private var micStreamer: MicStreamer? = null
    private var audioPlayer: AudioPlayer? = null
    /** A session opened ahead of time (while idle, before the user taps the mic) so the model
     *  has already ingested the persona + knowledge-base system instruction by the time the
     *  first real command arrives -- that ingestion is what makes the very first turn slow. */
    private var warmClient: LiveClient? = null
    private var isPrewarmReady = false
    /** Set when the user taps the mic while a warm session is still connecting (not yet ready);
     *  makes its setupComplete promote straight into a real, streaming session instead of just
     *  sitting there ready-and-unused. */
    private var activateWarmOnReady = false
    /** When the current [warmClient] was opened -- if nobody taps the mic before this goes
     *  stale, the server may have quietly timed out its side, so it's discarded and a fresh
     *  cold connect is used instead of promoting a possibly-dead socket. */
    private var warmClientCreatedAtMillis = 0L

    private val userTextBuffer = StringBuilder()
    private val assistantTextBuffer = StringBuilder()

    /** True only when the user tapped the mic to stop -- distinguishes a deliberate stop from
     *  the server closing the connection on its own (session/time limit), which should
     *  auto-reconnect instead of going idle. */
    private var userInitiatedStop = false

    /** Guards against a reconnect loop if the server keeps closing the connection immediately;
     *  reset only once a turn actually completes (not just on setupComplete -- a server that
     *  accepts setup and then immediately closes would otherwise reset this every time and
     *  never trip the cap). */
    private var consecutiveAutoReconnects = 0
    private val maxAutoReconnects = 3
    /** A question the server heard but never answered. It is asked again, as text, on a fresh connection. */
    private var retryQuestion: String? = null
    /** Set once the current question has been asked again, so a server that stays silent ends in an error, not a loop. */
    private var silentTurnRetried = false
    private val reconnectBackoffMs = listOf(500L, 2000L, 5000L)

    private val audio: AudioRouting = AudioRouting(audioManager, object : AudioFocusListener {
        /** Something else needs the speaker/mic -- most commonly an incoming call. Stop cleanly
         *  instead of continuing to stream mic audio into a session nobody can hear or respond to. */
        override fun onFocusLost() {
            if (uiState.value.voiceState != VoiceState.IDLE) {
                if (audio.isPhoneCallMode() && isSessionActive(uiState.value.voiceState)) pauseForCall() else stopSession()
            }
        }

        /** A short interruption (navigation prompt, notification sound): pause the mic and carry on afterwards. */
        override fun onFocusLostTemporarily() {
            // A ringing or answered phone call also arrives as a short loss, but it needs the
            // speaker, mic and audio mode this session holds, so Ultron is silenced until the call ends.
            if (audio.isPhoneCallMode() && isSessionActive(uiState.value.voiceState)) {
                pauseForCall()
            } else if (isSessionActive(uiState.value.voiceState)) {
                audio.pausedByFocusLoss = true
                micStreamer?.setMuted(true)
            }
        }

        override fun onFocusGained() {
            if (audio.pausedByFocusLoss) {
                audio.pausedByFocusLoss = false
                if (uiState.value.voiceState != VoiceState.SPEAKING || uiState.value.settings.bargeInEnabled) {
                    micStreamer?.setMuted(false)
                }
            }
        }
    })

    private val speech = SpeechDetector()

    private val timers = SessionTimers(
        scope = scope,
        onIdleTimeout = { if (uiState.value.voiceState != VoiceState.IDLE) stopSession() },
        onTurnTimeout = { onTurnTimedOut() },
        onSessionWarning = {
            if (isSessionActive(uiState.value.voiceState)) showToast("This session ends in 1 minute.", AppNotificationType.INFO)
        },
        onSessionLimit = {
            val state = uiState.value.voiceState
            if (state != VoiceState.IDLE && state != VoiceState.ERROR) {
                showToast("Session ended after 15 minutes. Tap the mic to start again.", AppNotificationType.SUCCESS)
                stopSession()
            }
        }
    )


    private val tools = ToolDispatcher(
        scope = scope,
        state = uiState,
        isCurrent = { client -> liveClient === client },
        startTurnTimeout = { timers.startTurnTimeout() },
        displayName = ToolRunner::displayName,
        execute = execute
    )

    /** When the current mic session began; a reconnect inside it replays only the exchanges from after this. */
    private var sessionStartedAtMs = 0L

    /** A new session starts fresh, with no earlier exchanges replayed into it (older ones can still be looked up with the
     *  history tools). Only a reconnect inside a running session replays that session's own exchanges, so it carries on. */
    private fun sessionStartHistory(history: List<ConversationTurn>, isAutoReconnect: Boolean) =
        if (isAutoReconnect) KnowledgeBaseProvider.liveHistoryTurns(history.filter { it.timestampMillis >= sessionStartedAtMs })
        else emptyList()


    /** A warm session was opened with whatever settings were current at the time -- if the user
     *  changes voice/language/prompt afterward, that warm session no longer
     *  reflects their choice. Discard it and open a new one with the fresh settings instead of
     *  leaving a stale one to be silently promoted on the next mic tap. */
    fun invalidateWarmSessionAndReprewarm() {
        cancelInFlightPrewarm()
        warmClient?.close()
        warmClient = null
        isPrewarmReady = false
        activateWarmOnReady = false
        prewarmIfPossible()
    }

    /** True when the device has a validated internet connection; assumes online if the check fails. */
    private fun isNetworkAvailable(): Boolean = try {
        val manager = application
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        true
    }

    fun startSession(isAutoReconnect: Boolean = false) {
        userInitiatedStop = false
        // A locked app (signed out or no active plan) never opens a Gemini session.
        if (!AccessGate.unlocked) return

        val apiKey = resolveApiKey()
        if (!hasGeminiAccess()) {
            uiState.update {
                it.copy(
                    voiceState = VoiceState.ERROR,
                    errorMessage = "The assistant isn't set up yet. Add a Gemini API key in Settings."
                )
            }
            // Send the user to Settings so they can add a key.
            openDeveloperPage()
            return
        }

        if (!isAutoReconnect) {
            historySessionId = java.util.UUID.randomUUID().toString()
            sessionStartedAtMs = System.currentTimeMillis()
            retryQuestion = null
            silentTurnRetried = false
            markVoiceUsageStart()
            sessionResumptionHandle = null
            timers.startSessionLimit()
        }

        // A stale warm session may have been silently timed out server-side -- discard it
        // rather than promote a socket that's likely dead, and fall through to a cold connect.
        if (warmClient != null && System.currentTimeMillis() - warmClientCreatedAtMillis > WARM_SESSION_TTL_MS) {
            warmClient?.close()
            warmClient = null
            isPrewarmReady = false
        }

        // A pre-warmed session already has the (slow to ingest) system instruction loaded --
        // consume it instead of opening a fresh connection, skipping straight to LISTENING.
        if (!isAutoReconnect && warmClient != null) {
            val client = warmClient!!
            if (isPrewarmReady) {
                warmClient = null
                isPrewarmReady = false
                liveClient = client
                uiState.update { it.copy(errorMessage = null) }
                beginStreaming(client, reusedWarm = true)
            } else {
                // Still connecting -- its own setupComplete will promote it once ready.
                activateWarmOnReady = true
                uiState.update {
                    it.copy(voiceState = VoiceState.CONNECTING, errorMessage = null)
                }
            }
            return
        }

        uiState.update {
            it.copy(
                voiceState = if (isAutoReconnect) VoiceState.RESETTING else VoiceState.CONNECTING,
                errorMessage = null
            )
        }

        sessionStartJob?.cancel()
        sessionStartJob = scope.launch {
            // Read settings and history directly from their stores rather than the mirrored
            // uiState copies -- right after a settings change or a history clear (New
            // conversation/Stop), those mirrors can still be one collector-tick stale, which
            // would otherwise bake stale/cleared data into this cold connect's system
            // instruction. See prewarmIfPossible() for the same reasoning.
            val history = conversationStore.recentTurns.first()
            val settings = settingsStore.settings.first()
            // The user may have tapped Stop again while this suspended -- don't open a session
            // they no longer want.
            if (userInitiatedStop) return@launch
            if (!isForeground && !backgroundSessionAllowed) return@launch
            val systemInstruction = KnowledgeBaseProvider.buildSystemInstruction(
                settings.systemPrompt,
                AssistantOptions.languageLabel(settings.languageCode),
                settings.assistantName,
                AssistantOptions.voiceGender(settings.voiceName),
                if (settings.memoryEnabled) memoriesForPrompt(memoryStore.current()) else emptyList(),
                ownerName = settings.ownerName
            )
            connectLiveSession(
                apiKey, systemInstruction, settings, sessionStartHistory(history, isAutoReconnect),
                resumeHandle = if (isAutoReconnect) sessionResumptionHandle else null
            )
        }
    }

    private fun connectLiveSession(
        apiKey: String,
        systemInstruction: String,
        settings: AssistantSettings,
        history: List<KnowledgeBaseProvider.HistoryTurn>,
        isWarm: Boolean = false,
        resumeHandle: String? = null
    ) {
        val client: LiveClient = GeminiLiveClient(apiKey, settings.liveModel)
        client.latency.event("session_origin", mapOf("mode" to when {
            isWarm -> "prewarm"
            uiState.value.voiceState == VoiceState.RESETTING -> "reconnect"
            else -> "cold"
        }))
        if (isWarm) {
            warmClient = client
            warmClientCreatedAtMillis = System.currentTimeMillis()
        } else {
            liveClient = client
        }

        // True as long as this client is still the one either field is pointing at -- i.e. it
        // hasn't since been replaced, promoted-and-superseded, or discarded. Guards every
        // callback below against acting on a stale session (e.g. a late onClosed arriving after
        // the user already started a different session, which previously could tear down the
        // NEW session's resources instead of being ignored).
        fun isCurrent(): Boolean = liveClient === client || warmClient === client

        client.connect(systemInstruction, settings, history, object : GeminiLiveClient.Listener {

            override fun onSetupComplete() {
                if (!isCurrent()) return
                if (isWarm && !activateWarmOnReady) {
                    // Nobody asked for it yet -- stay connected and idle, ready for the next tap.
                    isPrewarmReady = true
                    return
                }
                if (isWarm) {
                    warmClient = null
                    isPrewarmReady = false
                    activateWarmOnReady = false
                }
                liveClient = client
                beginStreaming(client, reusedWarm = isWarm)
            }

            override fun onContextUsage(totalTokens: Int) {
                if (liveClient !== client) return
                uiState.update { it.copy(liveContextTokens = totalTokens) }
            }

            override fun onAudioChunk(pcm: ByteArray, receivedAtMs: Long) {
                if (liveClient !== client) return
                // A tool result can be followed by a separate model turn. Its first audio is the
                // proof that the original visitor interaction has resumed.
                timers.noteAudioChunk()
                silentTurnRetried = false
                timers.cancelTurnTimeout()
                val turn = client.latency.turn()
                val firstAudio = turn?.audioReceived(receivedAtMs) == true
                if (!pausedByCall) audioPlayer?.enqueue(pcm, turn.takeIf { firstAudio })
                if (uiState.value.voiceState != VoiceState.SPEAKING) {
                    val speechAt = speech.lastSpeechAtMs
                    if (BuildConfig.DEBUG && speechAt > 0L) {
                        Log.d("AssistantLatency", "last-speech-to-first-audio=${SystemClock.elapsedRealtime() - speechAt}ms")
                    }
                    // Barge-in on: leave the mic live (VOICE_COMMUNICATION's echo cancellation
                    // is relied on to keep the assistant's own voice from being picked up as
                    // input) so the user can interrupt it mid-reply. Barge-in off: mute as before.
                    if (!uiState.value.settings.bargeInEnabled) {
                        micStreamer?.setMuted(true)
                    }
                    uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
                    timers.startSpeakingWatchdog(
                        isSpeaking = { uiState.value.voiceState == VoiceState.SPEAKING },
                        playbackHead = { audioPlayer?.playbackHeadFrames() },
                        hasPendingTools = { tools.hasPending },
                        onStalled = {
                            Log.w("AssistantError", "Speaking stalled for ${SessionTimers.SPEAKING_STALL_MS}ms; returning to listening")
                            finalizeTurnIfAny()
                            micStreamer?.setMuted(false)
                            speech.reset()
                            audioPlayer?.clearQueueForInterruption()
                            uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                        }
                    )
                }
            }

            override fun onInputTranscript(text: String) {
                if (liveClient !== client) return
                if (uiState.value.voiceState == VoiceState.LISTENING ||
                    uiState.value.voiceState == VoiceState.PROCESSING
                ) client.latency.turn()?.speech(SystemClock.elapsedRealtime(), "transcript_arrival")
                // Server transcription also counts as speech: quiet short queries may never
                // cross the local amplitude threshold used only for the UI/watchdog.
                if (uiState.value.voiceState == VoiceState.LISTENING) {
                    speech.markSpeech()
                }
                userTextBuffer.append(text)
                timers.resetIdle()
            }

            override fun onOutputTranscript(text: String) {
                if (liveClient !== client) return
                assistantTextBuffer.append(text)
                timers.resetIdle()
            }

            override fun onTurnComplete() {
                if (liveClient !== client) return
                client.latency.complete()
                timers.cancelTurnTimeout()
                if (tools.hasPending) {
                    // The model has yielded for local retrieval. Its eventual audio belongs to
                    // the same user/assistant transcript pair, so do not persist it yet. The filler
                    // it spoke ("one moment") still drains and hands the mic back, so a slow tool
                    // does not leave the screen on Speaking with the mic muted.
                    timers.resetIdle()
                    audioPlayer?.notifyTurnComplete()
                    return
                }
                // Every tool response was already sent and the model has now finished its follow-up turn,
                // possibly with no audio at all, so the turn ends here.
                // The reply to every tool is done, so no tool name may stay on screen into the next request.
                uiState.update { it.copy(activeTool = null) }
                finalizeTurnIfAny()
                // A full turn actually completed -- this session is genuinely working, so the
                // reconnect-loop guard can relax again.
                consecutiveAutoReconnects = 0
                timers.resetIdle()
                // Don't unmute yet -- AudioPlayer still has buffered/queued audio to actually
                // play out. Unmuting here would let the mic pick the tail of the assistant's own
                // reply back up as if it were the user talking. AudioPlayer's onPlaybackIdle
                // callback (passed in beginStreaming) does the real unmute once playback has
                // drained.
                audioPlayer?.notifyTurnComplete()
            }

            override fun onInterrupted() {
                if (liveClient !== client) return
                client.latency.cancel("interrupted")
                timers.cancelTurnTimeout()
                // Running tools are not cancelled here: the server names the calls it abandons in
                // toolCallCancellation. A non-blocking call (a text being sent) is still wanted after
                // the user talks over the filler, and dropping it lost the result the model waits for.
                timers.resetIdle()
                // Close out the interrupted reply as its own turn so it isn't merged with
                // whatever the user says next.
                finalizeTurnIfAny()
                // clearQueueForInterruption() itself fires onPlaybackIdle once the track is
                // flushed, which is the same callback that unmutes the mic and sets LISTENING.
                audioPlayer?.clearQueueForInterruption()
            }

            override fun onToolCall(calls: List<GeminiLiveClient.FunctionCall>) {
                if (liveClient !== client) return
                silentTurnRetried = false
                timers.cancelTurnTimeout()
                timers.resetIdle()
                recordUsage(UsageDay("", toolCalls = calls.size))
                tools.onToolCalls(client, calls)
            }

            override fun onToolCallCancellation(ids: List<String>) {
                if (liveClient !== client) return
                tools.onCancellation(client, ids)
            }

            override fun onGoAway(timeLeftMs: Long, closeNow: () -> Unit) {
                val busy = liveClient === client && (
                    uiState.value.voiceState == VoiceState.SPEAKING ||
                        uiState.value.voiceState == VoiceState.PROCESSING ||
                        tools.hasPending)
                if (!busy) {
                    closeNow()
                    return
                }
                // Reconnecting now would cut Ultron off mid-sentence. Wait for the reply to end, but never past
                // the time the server gave (minus a safety margin).
                pendingGoAway = closeNow
                goAwayJob?.cancel()
                val waitMs = if (timeLeftMs < 0) GO_AWAY_DEFAULT_WAIT_MS else (timeLeftMs - GO_AWAY_MARGIN_MS).coerceAtLeast(0L)
                goAwayJob = scope.launch {
                    delay(waitMs)
                    runPendingGoAway()
                }
            }

            override fun onResumptionHandle(handle: String) {
                // A warm session has no conversation yet, so only the live one's handle counts.
                if (liveClient === client) sessionResumptionHandle = handle
            }

            override fun onError(message: String) {
                if (!isCurrent()) return
                // A rejected setup may be the stale handle; the next start begins fresh anyway.
                sessionResumptionHandle = null
                client.latency.close("failed")
                if (isWarm && warmClient === client && !activateWarmOnReady) {
                    // Background prewarm failed before anyone asked for it -- discard silently,
                    // a normal cold connect will run next time the mic is tapped.
                    warmClient = null
                    isPrewarmReady = false
                    return
                }
                if (isWarm) {
                    warmClient = null
                    isPrewarmReady = false
                    activateWarmOnReady = false
                }
                releaseResources()
                uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        errorMessage = message
                    )
                }
            }

            override fun onClosed() {
                if (!isCurrent()) return
                client.latency.close("disconnected")
                if (isWarm && warmClient === client && !activateWarmOnReady) {
                    warmClient = null
                    isPrewarmReady = false
                    return
                }
                if (isWarm) {
                    warmClient = null
                    isPrewarmReady = false
                    activateWarmOnReady = false
                }
                releaseResources()
                val currentState = uiState.value.voiceState
                when {
                    currentState == VoiceState.ERROR -> Unit // already surfaced by onError
                    currentState == VoiceState.IDLE -> Unit // already stopped/never started streaming
                    userInitiatedStop -> uiState.update {
                        it.copy(voiceState = VoiceState.IDLE, liveContextTokens = 0)
                    }
                    !isNetworkAvailable() -> uiState.update {
                        it.copy(
                            voiceState = VoiceState.ERROR,
                            errorMessage = "No internet connection. Please check Wi-Fi and try again."
                        )
                    }
                    consecutiveAutoReconnects >= maxAutoReconnects -> uiState.update {
                        it.copy(
                            voiceState = VoiceState.ERROR,
                            errorMessage = "Session kept disconnecting. Please try again."
                        )
                    }
                    else -> {
                        // Server closed the connection on its own (session/time limit) --
                        // reconnect automatically, backing off a bit more each consecutive time.
                        consecutiveAutoReconnects++
                        uiState.update {
                            it.copy(voiceState = VoiceState.RESETTING)
                        }
                        val delayMs = reconnectBackoffMs.getOrElse(consecutiveAutoReconnects - 1) { reconnectBackoffMs.last() }
                        reconnectJob?.cancel()
                        reconnectJob = scope.launch {
                            delay(delayMs)
                            if ((isForeground || backgroundSessionAllowed) && !userInitiatedStop) startSession(isAutoReconnect = true)
                        }
                    }
                }
            }
        }, resumeHandle)
    }

    private fun beginStreaming(client: LiveClient, reusedWarm: Boolean = false) {
        if (liveClient !== client) return
        client.latency.event("stream_start", mapOf(
            "language" to uiState.value.settings.languageCode,
            "reused_warm" to reusedWarm,
            "connection_age_ms" to (client.latency.now() - client.latency.createdAtMs)
        ))
        tools.openScope()
        if (!audio.acquire()) {
            releaseResources()
            uiState.update {
                it.copy(
                    voiceState = VoiceState.ERROR,
                    errorMessage = "Couldn't use the microphone or speaker right now. Please try again."
                )
            }
            return
        }
        timers.resetIdle()
        speech.reset()

        var player: AudioPlayer? = null
        val createdPlayer = AudioPlayer(
            onPlaybackAmplitude = amplitude@{ amp ->
                if (liveClient !== client || audioPlayer !== player) return@amplitude
                voiceLevel.value = amp
            },
            onPlaybackIdle = playbackIdle@{
                // AudioTrack callbacks can arrive after a reconnect or after this player was
                // stopped. They must not unmute the current session's mic or change its UI.
                if (liveClient !== client || audioPlayer !== player) return@playbackIdle
                client.latency.playbackIdle()
                // Playback has actually finished (or was interrupted) -- safe to unmute now.
                micStreamer?.setMuted(false)
                speech.reset()
                // PROCESSING is included so a turn that completes with no audio (empty reply)
                // also returns to LISTENING here, instead of staying stuck until fresh speech
                // or the 2-minute idle timeout.
                if (uiState.value.voiceState == VoiceState.SPEAKING ||
                    uiState.value.voiceState == VoiceState.PROCESSING
                ) {
                    uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                }
                if (!tools.hasPending) runPendingGoAway()
            },
            onPlaybackFailure = playbackFailure@{ message ->
                if (liveClient !== client || audioPlayer !== player) return@playbackFailure
                releaseResources()
                uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        errorMessage = message
                    )
                }
            },
            latency = client.latency
        )
        player = createdPlayer
        if (!createdPlayer.start()) {
            releaseResources()
            uiState.update {
                it.copy(
                    voiceState = VoiceState.ERROR,
                    errorMessage = "Couldn't play audio right now. Please try again."
                )
            }
            return
        }
        audioPlayer = createdPlayer

        val mic = MicStreamer(
            onChunk = { chunk -> client.sendAudioChunk(chunk) },
            // A queued event from an earlier session's mic must not touch this session's state.
            onAmplitude = { amp ->
                if (liveClient === client && audioPlayer === createdPlayer) onMicAmplitude(amp)
            },
            onError = micError@{ message ->
                if (liveClient !== client || audioPlayer !== createdPlayer) return@micError
                releaseResources()
                uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        errorMessage = message
                    )
                }
            },
            sensitivity = uiState.value.settings.micSensitivity
        )
        // mic.start() returning false means it already called onError above (releasing
        // resources and publishing ERROR) -- must not fall through and overwrite that with
        // LISTENING, or assign a streamer whose AudioRecord never actually started.
        if (!mic.start()) return
        micStreamer = mic

        uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
        client.latency.event("stream_ready")

        // Re-ask the question the previous connection never answered.
        retryQuestion?.let { question ->
            retryQuestion = null
            if (client.sendText(question)) {
                userTextBuffer.append(question)
                speech.markSpeechWithoutClock()
                timers.startTurnTimeout()
                uiState.update { it.copy(voiceState = VoiceState.PROCESSING) }
            } else {
                silentTurnRetried = false
            }
        }
    }

    /** What the user has said so far in this turn (transcribed), for checking tool steps against it. */
    fun currentUserRequest(): String = userTextBuffer.toString().trim()

    private fun finalizeTurnIfAny() {
        val userText = userTextBuffer.toString().trim()
        val assistantText = assistantTextBuffer.toString().trim()
        userTextBuffer.clear()
        assistantTextBuffer.clear()

        if (userText.isNotEmpty() || assistantText.isNotEmpty()) {
            val sessionId = historySessionId
            scope.launch {
                // Best-effort: a failure to persist recent history (e.g. storage full) must not
                // crash the live conversation over what's only a "remember the last few turns"
                // convenience -- log it and move on, same as background prewarm failures.
                try {
                    val turn = ConversationTurn(userText, assistantText)
                    conversationStore.appendTurn(turn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AssistantError", "Failed to save conversation turn", e)
                }
                // Past conversations: keep this exchange for good and make it searchable in the background.
                try {
                    withContext(Dispatchers.IO) {
                        val id = conversationHistoryStore.append(sessionId, userText, assistantText, System.currentTimeMillis())
                        Log.i("AssistantMemory", "saved=${id != null} total=${conversationHistoryStore.count()}")
                    }
                    indexConversationHistory()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AssistantError", "Failed to save to long-term memory", e)
                }
            }
        }
    }

    fun stopSession() {
        recordVoiceUsage()
        sessionStartJob?.cancel()
        sessionStartJob = null
        timers.cancelSessionLimit()
        sessionResumptionHandle = null
        reconnectJob?.cancel()
        reconnectJob = null
        userInitiatedStop = true
        consecutiveAutoReconnects = 0
        // If a warm session was mid-connect and about to be promoted, cancel that -- it'll just
        // finish connecting in the background and sit ready for the next tap instead.
        activateWarmOnReady = false
        releaseResources()
        // Discard any partial turn -- a manual stop mid-turn means it was never finalized, and
        // leaving buffered text around would otherwise bleed into whatever turn comes next.
        userTextBuffer.clear()
        assistantTextBuffer.clear()
        retryQuestion = null
        silentTurnRetried = false
        uiState.update {
            it.copy(voiceState = VoiceState.IDLE, liveContextTokens = 0)
        }
        // The history stays on the device for the history tools, but the next session starts fresh and is not seeded with it.
        rewarmAfterStop()
    }

    /** Discards any warm session -- it was built before this conversation's last turns -- and
     *  prewarms a new one, which reads the history again and so includes them. */
    private fun rewarmAfterStop() {
        cancelInFlightPrewarm()
        warmClient?.close()
        warmClient = null
        isPrewarmReady = false
        scope.launch { prewarmIfPossible() }
    }

    /** True while a prewarm coroutine is between its synchronous entry checks and actually
     *  setting [warmClient] -- guards against two overlapping callers (e.g. the ViewModel's own
     *  init block and MainActivity's onStart firing back-to-back on cold launch) both passing
     *  the "no warm client yet" check and opening two warm connections. */
    private var prewarmInFlight = false

    /** Bumped whenever settings, memory or history change; a prewarm that read its inputs under an
     *  older generation is dropped instead of becoming a warm session built from stale data. */
    private var prewarmGeneration = 0

    fun cancelInFlightPrewarm() {
        prewarmGeneration++
        prewarmJob?.cancel()
        prewarmJob = null
        prewarmInFlight = false
    }

    /** Opens a session ahead of time so its (slow) system-instruction ingestion is already done
     *  by the time the user actually taps the mic. No-op if one is already warm/warming, the app
     *  has no key yet, or a real session is already active. */
    fun prewarmIfPossible() {
        if (!keysLoaded) return
        if (!AccessGate.unlocked) return
        if (!isForeground) return
        if (warmClient != null || prewarmInFlight) return
        if (uiState.value.voiceState != VoiceState.IDLE) return
        val apiKey = resolveApiKey()
        if (!hasGeminiAccess()) return

        prewarmInFlight = true
        val generation = prewarmGeneration
        prewarmJob = scope.launch {
            try {
                // Wait a moment first: many warm sessions were never used (the app was only passing through, or a
                // setting was changed several times). Each change cancels this job and starts the wait again.
                delay(PREWARM_DELAY_MS)
                // Read the settings and history directly from their stores rather than the
                // mirrored uiState copy -- that copy is only updated by a separate collector, so
                // right after a setting changes or history is cleared (or right at app launch,
                // before that collector's first emission), it can still be one step stale.
                // DataStore's Flow always reflects the latest committed value on a fresh read.
                val history = conversationStore.recentTurns.first()
                val settings = settingsStore.settings.first()
                val systemInstruction = KnowledgeBaseProvider.buildSystemInstruction(
                    settings.systemPrompt,
                    AssistantOptions.languageLabel(settings.languageCode),
                    settings.assistantName,
                    AssistantOptions.voiceGender(settings.voiceName),
                    if (settings.memoryEnabled) memoriesForPrompt(memoryStore.current()) else emptyList(),
                    ownerName = settings.ownerName
                )

                if (generation != prewarmGeneration) return@launch
                if (!isForeground || warmClient != null || uiState.value.voiceState != VoiceState.IDLE) return@launch
                connectLiveSession(
                    apiKey, systemInstruction, settings, sessionStartHistory(history, isAutoReconnect = false), isWarm = true
                )
            } finally {
                // A newer prewarm may already own the flag; only the current generation resets it.
                if (generation == prewarmGeneration) prewarmInFlight = false
            }
        }
    }

    private fun runPendingGoAway() {
        goAwayJob?.cancel()
        goAwayJob = null
        val close = pendingGoAway ?: return
        pendingGoAway = null
        close()
    }

    private fun releaseResources() {
        goAwayJob?.cancel()
        goAwayJob = null
        pendingGoAway = null
        timers.cancelSpeakingWatchdog()
        liveClient?.latency?.close(if (userInitiatedStop) "stopped" else "failed")
        tools.shutdown()
        timers.cancelIdle()
        timers.cancelTurnTimeout()
        micStreamer?.stop()
        micStreamer = null
        uiState.update { it.copy(isMicMuted = false) }
        audioPlayer?.stop()
        audioPlayer = null
        liveClient?.close()
        liveClient = null
        releaseCommunicationAudio()
        // Every session boundary (error, close, mic/player-start failure, manual stop, turn
        // timeout) goes through here -- discard any partial transcript rather than let it bleed
        // into whatever turn comes next (e.g. after an auto-reconnect). An interrupted turn was
        // never genuinely completed, so it isn't finalized/persisted either.
        userTextBuffer.clear()
        assistantTextBuffer.clear()
    }

    /** Fires when the reply watchdog (see [SessionTimers]) elapses with no audio/toolCall/turnComplete/error
     *  for the turn currently being waited on -- surfaces a real error instead of leaving the UI
     *  stuck on PROCESSING until the much longer, silent idle timeout eventually resets it. */
    private fun onTurnTimedOut() {
        if (uiState.value.voiceState != VoiceState.PROCESSING) return
        // No input transcript arrived for this turn: the local amplitude heuristic fired on noise
        // (a cough, background chatter) that the server's VAD rightly ignored, so no reply is
        // coming. Quietly resume listening instead of killing the session with an error.
        if (userTextBuffer.isBlank()) {
            liveClient?.latency?.cancel("noise_timeout")
            speech.reset()
            uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
            return
        }
        // Seen live: the server transcribes the question and then sends nothing, while a new
        // connection a moment later answers normally. Reconnect once and ask again, so the user
        // doesn't have to tap the mic and repeat themselves.
        val question = userTextBuffer.toString().trim()
        if (!silentTurnRetried && question.isNotEmpty() && isNetworkAvailable()) {
            Log.w("AssistantError", "No reply after ${SessionTimers.TURN_RESPONSE_TIMEOUT_MS}ms; reconnecting and asking again")
            silentTurnRetried = true
            retryQuestion = question
            // Resuming would put us back into the server session that stopped answering.
            sessionResumptionHandle = null
            releaseResources()
            uiState.update { it.copy(voiceState = VoiceState.RESETTING) }
            startSession(isAutoReconnect = true)
            return
        }
        silentTurnRetried = false
        retryQuestion = null
        Log.w("AssistantError", "Turn timed out after ${SessionTimers.TURN_RESPONSE_TIMEOUT_MS}ms with no server response")
        releaseResources()
        uiState.update {
            it.copy(
                voiceState = VoiceState.ERROR,
                errorMessage = "The assistant didn't respond in time. Please try again."
            )
        }
    }

    /** Mic level handling; [SpeechDetector] decides, this acts on it. */
    private fun onMicAmplitude(amp: Float) {
        if (uiState.value.isMicMuted) return
        val voiceState = uiState.value.voiceState
        if (voiceState != VoiceState.SPEAKING) voiceLevel.value = visualLevel(amp)
        when (val result = speech.onAmplitude(amp, voiceState)) {
            is SpeechDetector.Result.Speech -> {
                if (result.inConversation) liveClient?.latency?.turn()?.speech(result.at, "rms")
                if (result.wasProcessing) {
                    timers.cancelTurnTimeout()
                    uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
                }
            }
            is SpeechDetector.Result.SilenceEnded -> {
                Log.i("AssistantLatency", "local speech end after ${result.silenceMs}ms silence")
                liveClient?.latency?.turn()?.event("local_silence_detected", mapOf(
                    "silence_ms" to result.silenceMs
                ))
                // UI/watchdog only. Keep streaming and let server VAD finalize the turn, as
                // stopping here can discard short speech and clip the next utterance.
                timers.startTurnTimeout()
                uiState.update { it.copy(voiceState = VoiceState.PROCESSING) }
            }
            SpeechDetector.Result.Quiet -> Unit
        }
    }

    /** Silences Ultron for the length of a phone call (ringing or answered) without ending the session. */
    private fun pauseForCall() {
        if (pausedByCall) return
        pausedByCall = true
        micStreamer?.setCallPaused(true)
        audioPlayer?.clearQueueForInterruption()
        timers.cancelIdle()
        callWatchJob?.cancel()
        callWatchJob = scope.launch {
            // The call ends when the audio mode leaves ringing/in-call. Two clean checks in a row avoid
            // resuming in the short gap between the ringtone and the call itself.
            var clear = 0
            while (clear < 2) {
                delay(1_000)
                clear = if (audio.isPhoneCallMode()) 0 else clear + 1
            }
            resumeAfterCall()
        }
    }

    private fun resumeAfterCall() {
        if (!pausedByCall) return
        pausedByCall = false
        callWatchJob = null
        if (!isSessionActive(uiState.value.voiceState)) return
        // The call took the audio mode and focus; claim them back for the voice session.
        audio.reclaim()
        micStreamer?.setCallPaused(false)
        timers.resetIdle()
        uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
    }

    private fun releaseCommunicationAudio() {
        audio.release()
        pausedByCall = false
        callWatchJob?.cancel()
        callWatchJob = null
    }

    @Volatile var keysLoaded = false

    fun setMicSensitivity(sensitivity: MicSensitivity) {
        micStreamer?.setSensitivity(sensitivity)
    }

    fun setUserMuted(muted: Boolean) {
        micStreamer?.setUserMuted(muted)
    }

    /** Called when the Activity actually stops (not just loses focus, e.g. a permission
     *  dialog) -- Android will block background mic use anyway, so a session left running here
     *  would otherwise sit silently stuck in a "Listening..."/"Speaking..." state. */
    fun onAppBackgrounded() {
        isForeground = false
        warmClient?.close()
        warmClient = null
        isPrewarmReady = false
        if (isSessionActive(uiState.value.voiceState)) {
            // The microphone foreground service (see AppForegroundService) keeps this session
            // alive and visible in the notification; Stop there or the idle timeout ends it.
            backgroundSessionAllowed = true
        } else if (uiState.value.voiceState != VoiceState.IDLE) {
            stopSession()
        }
    }

    /** The subscription screen closed the app: end everything that is running. */
    fun onAccessLocked() {
        if (isSessionActive(uiState.value.voiceState)) stopSession()
        cancelInFlightPrewarm()
        warmClient?.close()
        warmClient = null
        isPrewarmReady = false
        activateWarmOnReady = false
    }

    /** Called when the Activity comes back to the foreground -- re-arm the warm session so the
     *  next mic tap is fast again. */
    fun onAppForegrounded() {
        isForeground = true
        backgroundSessionAllowed = false
        prewarmIfPossible()
    }

    fun onCleared() {
        prewarmJob?.cancel()
        stopSession()
        warmClient?.close()
        warmClient = null
        isPrewarmReady = false
    }
}
