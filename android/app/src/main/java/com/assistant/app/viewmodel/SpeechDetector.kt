package com.assistant.app.viewmodel

import android.os.SystemClock

/**
 * Approximates "the user has stopped talking and the assistant hasn't replied yet" purely from mic RMS, since the
 * Live protocol doesn't expose a distinct end-of-user-turn signal to this client. Loud enough audio counts as speech
 * and resets the silence clock; once real speech has been heard this turn and the mic then stays quiet for
 * [SILENCE_TO_PROCESSING_MS], the turn counts as finished so the UI doesn't look stuck while a reply is generated.
 * It only decides; the caller acts on the [Result].
 */
internal class SpeechDetector(private val now: () -> Long = SystemClock::elapsedRealtime) {
    companion object {
        /** Rough mic-RMS floor above which audio is treated as "the user is talking" rather than
         *  ambient noise. A guess, not measured against real kiosk-room noise -- may need tuning on-device. */
        const val SPEECH_AMPLITUDE_THRESHOLD = 0.02f
        /** How long the mic has to stay quiet (after having heard real speech) before assuming
         *  the user is done talking and the assistant is now "thinking". */
        const val SILENCE_TO_PROCESSING_MS = 900L
    }

    sealed interface Result {
        /** Nothing to do. */
        data object Quiet : Result
        /** Loud audio at [at]. [wasProcessing] means the user started talking again while the UI showed PROCESSING;
         *  [inConversation] means the state was LISTENING or PROCESSING, so the latency trace notes the speech. */
        data class Speech(val at: Long, val wasProcessing: Boolean, val inConversation: Boolean) : Result
        /** Speech was heard and the mic has been quiet long enough. */
        data class SilenceEnded(val silenceMs: Long) : Result
    }

    private var lastLoudAt = 0L
    private var heardSpeechThisTurn = false
    /** When loud audio was last heard this turn, 0 when none was; read from other threads. */
    @Volatile var lastSpeechAtMs = 0L
        private set

    /** Starts a new turn: nothing heard yet. */
    fun reset() {
        lastLoudAt = now()
        heardSpeechThisTurn = false
        lastSpeechAtMs = 0L
    }

    /** Speech is known to have happened (a transcript arrived, or a question was sent as text) without loud audio. */
    fun markSpeech() {
        heardSpeechThisTurn = true
        lastLoudAt = now()
    }

    /** A question was asked as text: counts as heard speech, but the silence clock is left alone. */
    fun markSpeechWithoutClock() {
        heardSpeechThisTurn = true
    }

    fun onAmplitude(amp: Float, state: VoiceState): Result {
        val at = now()
        if (amp >= SPEECH_AMPLITUDE_THRESHOLD) {
            lastLoudAt = at
            lastSpeechAtMs = at
            heardSpeechThisTurn = true
            return Result.Speech(
                at = at,
                wasProcessing = state == VoiceState.PROCESSING,
                inConversation = state == VoiceState.LISTENING || state == VoiceState.PROCESSING
            )
        }
        if (state == VoiceState.LISTENING && heardSpeechThisTurn && at - lastLoudAt >= SILENCE_TO_PROCESSING_MS) {
            return Result.SilenceEnded(at - lastLoudAt)
        }
        return Result.Quiet
    }
}
