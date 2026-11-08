package com.assistant.app.viewmodel

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/** What the voice session does when something else takes or gives back the speaker and microphone. */
internal interface AudioFocusListener {
    /** Focus is gone for good, for example a phone call or another media app. */
    fun onFocusLost()
    /** Focus is gone for a moment, for example a navigation prompt; it usually comes back. */
    fun onFocusLostTemporarily()
    /** Focus is back after a temporary loss. */
    fun onFocusGained()
}

/**
 * Takes the speaker and microphone for a voice session and gives them back afterwards: audio focus, the
 * communication audio mode, speaker routing and the voice-call volume. What to do when focus changes is up to the
 * [listener]; this class only keeps the Android audio state.
 */
internal class AudioRouting(
    private val audioManager: AudioManager,
    private val listener: AudioFocusListener
) {
    private var focusRequest: AudioFocusRequest? = null
    // The mode/routing this session's audio actually owns -- saved only once focus is granted
    // and this session has genuinely changed them, so release can restore whatever was active
    // before (which may not be NORMAL/speaker-off, e.g. another app already in a call).
    private var previousMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var usedCommunicationDevice = false
    /** Voice-call volume before this session raised it from 0, restored when the session ends. */
    private var previousVoiceCallVolume: Int? = null
    private var raisedVoiceCallVolume: Int? = null

    /** True while a short audio-focus loss has the mic paused; cleared when focus returns. */
    var pausedByFocusLoss = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> listener.onFocusLost()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> listener.onFocusLostTemporarily()
            AudioManager.AUDIOFOCUS_GAIN -> listener.onFocusGained()
        }
    }

    /** True while the phone rings or a call is running. */
    fun isPhoneCallMode() = audioManager.mode == AudioManager.MODE_RINGTONE || audioManager.mode == AudioManager.MODE_IN_CALL

    /** Requests audio focus first, before touching any routing -- returns false (having changed
     *  nothing) if it's denied, e.g. an incoming call already holds it. Only on a grant does it
     *  go on to claim MODE_IN_COMMUNICATION/speakerphone/volume, so a denied session never starts
     *  recording/playback into audio nobody can hear or that steps on another app. */
    fun acquire(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return false
        }
        focusRequest = request

        // MicStreamer records with VOICE_COMMUNICATION for platform echo cancellation, which needs
        // the device in communication audio mode -- otherwise the audio policy engine can leave
        // the very first playback (AudioTrack) silent while routing settles. That mode defaults
        // routing to the earpiece (phone-call behavior), so speakerphone is forced on explicitly
        // or nothing is audible on a kiosk tablet.
        previousMode = audioManager.mode
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToSpeaker()
        // MODE_IN_COMMUNICATION routes assistant playback through the voice-call volume curve on
        // some OEMs, not the media one -- since this app never makes a real call, that stream's
        // volume can sit at 0 (never touched by the user's normal volume-button use), making
        // the assistant's reply completely silent even though everything else (queueing,
        // AudioTrack state, speakerphone routing) is working. Raise it to an audible floor if
        // it's muted.
        val voiceCallMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == 0) {
            val raised = (voiceCallMax * 0.7f).toInt().coerceAtLeast(1)
            previousVoiceCallVolume = 0
            raisedVoiceCallVolume = raised
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, raised, 0)
        }
        return true
    }

    /** A phone call took the audio mode, speaker routing and focus; claims them back for the voice session. */
    fun reclaim() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToSpeaker()
        focusRequest?.let { audioManager.requestAudioFocus(it) }
    }

    /** Sends communication-mode audio to the built-in speaker. A phone call resets this routing, so it is also done again when a call ends. */
    private fun routeToSpeaker() {
        usedCommunicationDevice = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // isSpeakerphoneOn is deprecated and unreliable on Android 12+; pick the built-in speaker explicitly.
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            usedCommunicationDevice = speaker != null && audioManager.setCommunicationDevice(speaker)
        }
        if (!usedCommunicationDevice) audioManager.isSpeakerphoneOn = true
    }

    fun release() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        // Restore whatever mode/routing was active before this session claimed it -- not always
        // NORMAL/speaker-off (e.g. another app may already have been mid-call).
        pausedByFocusLoss = false
        if (usedCommunicationDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            usedCommunicationDevice = false
        } else {
            previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
        }
        previousMode?.let { audioManager.mode = it }
        // Only undo the raise if the user (or the volume tool) has not changed the volume since.
        previousVoiceCallVolume?.let {
            runCatching {
                if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == raisedVoiceCallVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, it, 0)
                }
            }
        }
        previousVoiceCallVolume = null
        raisedVoiceCallVolume = null
        previousSpeakerphoneOn = null
        previousMode = null
    }
}
