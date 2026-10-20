package com.assistant.app.data

/** A single user/assistant exchange, used both for on-screen transcript and for
 *  priming a new Gemini Live session with recent context. */
data class ConversationTurn(
    val userText: String,
    val assistantText: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    /** Files the user sent with this turn (typed chat only); the bytes live in [ChatAttachmentStore]. */
    val attachments: List<AttachmentRef> = emptyList()
)

/** Where a sent file is saved, relative to the attachment folder. */
data class AttachmentRef(val name: String, val mimeType: String, val path: String, val size: Int)
