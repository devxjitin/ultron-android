package com.assistant.app.data

/** A single user/assistant exchange, used both for on-screen transcript and for
 *  priming a new Gemini Live session with recent context. */
data class ConversationTurn(
    val userText: String,
    val assistantText: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
