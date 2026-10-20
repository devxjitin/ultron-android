package com.assistant.app.ui

/** Visitor-facing home screen text. */
data class VisitorStrings(
    val justAMoment: String,
    val tapToSpeak: String,
    val listening: String,
    val micMuted: String,
    val mute: String,
    val unmute: String,
    val thinking: String,
    val speaking: String,
    val connectingCancel: String,
    val refreshingCancel: String,
    val micRationale: String,
    val micDenied: String
)

private val english = VisitorStrings(
    justAMoment = "Just a moment...",
    tapToSpeak = "Tap to speak",
    listening = "Listening...",
    micMuted = "Mic muted. Tap the mic-off button to unmute",
    mute = "Mute microphone",
    unmute = "Unmute microphone",
    thinking = "Thinking...",
    speaking = "Speaking...",
    connectingCancel = "Connecting... Tap mic to cancel",
    refreshingCancel = "Refreshing session... Tap mic to cancel",
    micRationale = "Assistant needs microphone access to hear your questions.",
    micDenied = "Microphone access was denied. Open Settings to allow it."
)

/** The app UI always stays in English; the Language setting only changes the spoken language. */
fun visitorStrings(): VisitorStrings = english
