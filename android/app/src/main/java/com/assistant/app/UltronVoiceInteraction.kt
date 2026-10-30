package com.assistant.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * What Android needs before it lists Ultron as a digital assistant: a voice interaction service with a session service.
 * Holding home or power starts a session, which only opens [MainActivity] with the ASSIST action, and the app takes over.
 */
class UltronVoiceInteractionService : VoiceInteractionService()

class UltronVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = UltronVoiceSession(this)
}

private class UltronVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startAssistantActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_ASSIST))
        hide()
    }
}

/** Declared only because the assistant role expects a recognition service next to the interaction service; Ultron listens through its own Live session. */
class UltronRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
