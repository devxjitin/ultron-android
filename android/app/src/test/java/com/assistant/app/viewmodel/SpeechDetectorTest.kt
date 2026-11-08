package com.assistant.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDetectorTest {
    private var clock = 10_000L
    private val detector = SpeechDetector { clock }
    private val loud = 0.5f
    private val quiet = 0.001f

    @Test fun `quiet audio before any speech does nothing`() {
        detector.reset()
        clock += 5_000
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.LISTENING))
    }

    @Test fun `loud audio counts as speech and records when`() {
        detector.reset()
        clock += 100
        val result = detector.onAmplitude(loud, VoiceState.LISTENING) as SpeechDetector.Result.Speech
        assertEquals(clock, result.at)
        assertEquals(clock, detector.lastSpeechAtMs)
        assertTrue(result.inConversation)
        assertEquals(false, result.wasProcessing)
    }

    @Test fun `speech while processing says the user is talking again`() {
        detector.reset()
        val result = detector.onAmplitude(loud, VoiceState.PROCESSING) as SpeechDetector.Result.Speech
        assertTrue(result.wasProcessing)
        assertTrue(result.inConversation)
    }

    @Test fun `speech while the assistant speaks is not part of the conversation trace`() {
        detector.reset()
        val result = detector.onAmplitude(loud, VoiceState.SPEAKING) as SpeechDetector.Result.Speech
        assertEquals(false, result.inConversation)
    }

    @Test fun `silence after speech ends the turn only after the delay`() {
        detector.reset()
        detector.onAmplitude(loud, VoiceState.LISTENING)
        clock += SpeechDetector.SILENCE_TO_PROCESSING_MS - 1
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.LISTENING))
        clock += 1
        assertEquals(
            SpeechDetector.Result.SilenceEnded(SpeechDetector.SILENCE_TO_PROCESSING_MS),
            detector.onAmplitude(quiet, VoiceState.LISTENING)
        )
    }

    @Test fun `silence only ends the turn while listening`() {
        detector.reset()
        detector.onAmplitude(loud, VoiceState.LISTENING)
        clock += 5_000
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.PROCESSING))
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.SPEAKING))
    }

    @Test fun `a transcript counts as speech and restarts the silence clock`() {
        detector.reset()
        clock += 5_000
        detector.markSpeech()
        clock += SpeechDetector.SILENCE_TO_PROCESSING_MS - 1
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.LISTENING))
        clock += 1
        assertTrue(detector.onAmplitude(quiet, VoiceState.LISTENING) is SpeechDetector.Result.SilenceEnded)
    }

    @Test fun `asking a question as text counts as speech but keeps the old silence clock`() {
        detector.reset()
        clock += 5_000
        detector.markSpeechWithoutClock()
        assertTrue(detector.onAmplitude(quiet, VoiceState.LISTENING) is SpeechDetector.Result.SilenceEnded)
    }

    @Test fun `reset forgets heard speech`() {
        detector.onAmplitude(loud, VoiceState.LISTENING)
        detector.reset()
        clock += 5_000
        assertEquals(SpeechDetector.Result.Quiet, detector.onAmplitude(quiet, VoiceState.LISTENING))
        assertEquals(0L, detector.lastSpeechAtMs)
    }
}
