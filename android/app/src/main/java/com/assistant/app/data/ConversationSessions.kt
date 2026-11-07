package com.assistant.app.data

/** One voice session: every exchange said between starting and stopping the assistant, oldest first. */
data class SessionGroup(val sessionId: String, val turns: List<StoredTurn>) {
    val startMillis: Long get() = turns.first().timestampMillis
    val endMillis: Long get() = turns.last().timestampMillis

    /** What the session was about: the first thing the user said. */
    val title: String
        get() = turns.firstOrNull { it.userText.isNotBlank() }?.userText?.trim()
            ?: turns.firstOrNull { it.assistantText.isNotBlank() }?.assistantText?.trim().orEmpty()
}

/** Groups saved exchanges into sessions, the most recent session first. */
fun groupBySession(turns: List<StoredTurn>): List<SessionGroup> =
    turns.groupBy { it.sessionId }
        .map { (id, items) -> SessionGroup(id, items.sortedBy { it.timestampMillis }) }
        .sortedByDescending { it.endMillis }

/** Sessions that mention every word of [query] anywhere in what was said. An empty query keeps everything. */
fun filterSessions(sessions: List<SessionGroup>, query: String): List<SessionGroup> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return sessions
    return sessions.filter { session ->
        val text = session.turns.joinToString(" ") { "${it.userText} ${it.assistantText}" }.lowercase()
        words.all { it in text }
    }
}
