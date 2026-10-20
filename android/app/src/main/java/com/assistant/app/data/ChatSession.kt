package com.assistant.app.data

/** One saved chat: every turn exchanged between a mic start and the matching stop. */
data class ChatSession(
    val id: String,
    val startedAtMillis: Long,
    val turns: List<ConversationTurn>,
    /** Short title the model wrote from the whole conversation; null until one has been made. */
    val customTitle: String? = null
) {
    /** The model's title for the chat; until it exists, the first thing the user said, as one short line. */
    val title: String
        get() {
            customTitle?.takeIf { it.isNotBlank() }?.let { return it }
            val first = turns.firstOrNull { it.userText.isNotBlank() }?.userText
                ?.replace(Regex("\\s+"), " ")?.trim()
                ?: turns.firstNotNullOfOrNull { turn -> turn.attachments.firstOrNull()?.name }
                ?: return "Conversation"
            return if (first.length > MAX_TITLE_CHARS) first.take(MAX_TITLE_CHARS).trimEnd() + "..." else first
        }

    private companion object {
        const val MAX_TITLE_CHARS = 40
    }
}
