package com.assistant.app.viewmodel

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The clocks of one voice session: the idle timeout, the wait for the model's reply, the stall watchdog while it
 * speaks, and the hard session limit. What to do when one fires is up to the callbacks; this class only counts.
 */
internal class SessionTimers(
    private val scope: CoroutineScope,
    /** No conversational activity for [idleTimeoutMs]. */
    private val onIdleTimeout: () -> Unit,
    /** The model sent nothing for [turnResponseTimeoutMs] after the user finished or a tool result went back. */
    private val onTurnTimeout: () -> Unit,
    /** One minute before the session limit. */
    private val onSessionWarning: () -> Unit,
    /** The session reached its limit. */
    private val onSessionLimit: () -> Unit,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val turnResponseTimeoutMs: Long = TURN_RESPONSE_TIMEOUT_MS,
    private val maxSessionMs: Long = MAX_SESSION_MS,
    private val sessionWarningMs: Long = SESSION_WARNING_MS,
    private val speakingPollMs: Long = SPEAKING_POLL_MS,
    private val speakingStallMs: Long = SPEAKING_STALL_MS,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    companion object {
        /** No conversational activity (transcript text or a turn boundary) for this long while
         *  a session is active auto-stops it and clears saved history, so a later session
         *  doesn't inherit a stale leftover conversation or an abandoned open mic. */
        const val IDLE_TIMEOUT_MS: Long = 120_000L
        /** Hard cap on one session, counted from the user's mic tap. It then stops and the user
         *  taps the mic to start again; auto-reconnects inside the cap don't extend it. */
        const val MAX_SESSION_MS: Long = 15 * 60_000L
        /** How long before [MAX_SESSION_MS] the user is told the session is about to end. */
        const val SESSION_WARNING_MS = 60_000L
        /** How long to wait, once the user has finished speaking (local UI heuristic) or a
         *  tool response has been sent back, for the model to produce SOMETHING -- audio,
         *  another tool call, or turnComplete. The Live protocol has no server-side "I'm still
         *  thinking" heartbeat, so a turn that silently never resumes (seen live: server
         *  transcribes the input, then sends nothing at all -- no audio/toolCall/error/close)
         *  would otherwise leave the UI stuck on PROCESSING until the unrelated 2-minute
         *  [IDLE_TIMEOUT_MS] silently resets the whole session with no explanation. */
        const val TURN_RESPONSE_TIMEOUT_MS: Long = 15_000L
        /** SPEAKING with no new audio chunk and no playback progress for this long means the turn
         *  ended without the idle signal that normally returns the UI to LISTENING. */
        const val SPEAKING_STALL_MS = 10_000L
        const val SPEAKING_POLL_MS = 2_000L
    }

    private var idleJob: Job? = null
    private var turnJob: Job? = null
    private var speakingJob: Job? = null
    private var limitJob: Job? = null
    private var lastAudioChunkAtMs = 0L

    /** Restarts the idle-timeout countdown -- called whenever there's a sign of real
     *  conversational activity. If nothing resets it for [idleTimeoutMs], [onIdleTimeout] runs. */
    fun resetIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleTimeoutMs)
            onIdleTimeout()
        }
    }

    fun cancelIdle() {
        idleJob?.cancel()
        idleJob = null
    }

    /** Starts (restarting if one's already running) the [turnResponseTimeoutMs] watchdog for
     *  the reply currently being awaited -- see the constant's doc for why this exists. */
    fun startTurnTimeout() {
        turnJob?.cancel()
        turnJob = scope.launch {
            delay(turnResponseTimeoutMs)
            onTurnTimeout()
        }
    }

    fun cancelTurnTimeout() {
        turnJob?.cancel()
        turnJob = null
    }

    /** Ends the session [maxSessionMs] after the user started it; auto-reconnects inside the cap don't extend it. */
    fun startSessionLimit() {
        limitJob?.cancel()
        limitJob = scope.launch {
            delay(maxSessionMs - sessionWarningMs)
            onSessionWarning()
            delay(sessionWarningMs)
            onSessionLimit()
        }
    }

    fun cancelSessionLimit() {
        limitJob?.cancel()
        limitJob = null
    }

    /** A chunk of the model's voice arrived; the stall watchdog counts from here. */
    fun noteAudioChunk() {
        lastAudioChunkAtMs = now()
    }

    /**
     * Safety net for the SPEAKING state: if audio stops arriving and the track stops advancing
     * without the normal turn-complete/idle signal, [onStalled] runs instead of the screen freezing.
     * Stops quietly once [isSpeaking] is false or [playbackHead] has no player any more.
     */
    fun startSpeakingWatchdog(
        isSpeaking: () -> Boolean,
        playbackHead: () -> Long?,
        hasPendingTools: () -> Boolean,
        onStalled: () -> Unit
    ) {
        speakingJob?.cancel()
        speakingJob = scope.launch {
            var lastHead = -1L
            var lastProgressAt = now()
            while (true) {
                delay(speakingPollMs)
                if (!isSpeaking()) return@launch
                val head = playbackHead() ?: return@launch
                val at = now()
                if (head != lastHead || hasPendingTools()) {
                    lastHead = head
                    lastProgressAt = at
                }
                if (at - maxOf(lastProgressAt, lastAudioChunkAtMs) >= speakingStallMs) {
                    onStalled()
                    return@launch
                }
            }
        }
    }

    fun cancelSpeakingWatchdog() {
        speakingJob?.cancel()
        speakingJob = null
    }
}
