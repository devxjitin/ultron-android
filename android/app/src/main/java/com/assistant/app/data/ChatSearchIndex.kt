package com.assistant.app.data

enum class SearchScope(val label: String) { ALL("All"), TITLES("Titles"), MESSAGES("Messages"), FILES("Files") }

/** One chat that matched: [turnIndex] is the exchange to scroll to when the chat is opened, [snippet] the text around the match. */
class ChatSearchHit(val session: ChatSession, val turnIndex: Int?, val snippet: String?, val matches: Int)

/**
 * Searchable copy of the saved chats. The lower-cased text is prepared once per list of chats (off the main thread),
 * so each search only compares words and no keystroke re-reads the whole history.
 */
class ChatSearchIndex(sessions: List<ChatSession>) {
    private class TurnText(val user: String, val assistant: String, val files: String) {
        val userLower = user.lowercase()
        val assistantLower = assistant.lowercase()
        val filesLower = files.lowercase()
    }

    private class Entry(val session: ChatSession, val titleLower: String, val turns: List<TurnText>)

    private val entries = sessions.map { session ->
        Entry(session, session.title.lowercase(), session.turns.map { TurnText(it.userText, it.assistantText, it.attachments.joinToString(" ") { f -> f.name }) })
    }

    /** Chats containing every word of [tokens] (already lower-case) inside [scope], best match first. */
    fun search(tokens: List<String>, scope: SearchScope, limit: Int = MAX_RESULTS): List<ChatSearchHit> {
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val parts = parts(entry, scope)
            if (!tokens.all { token -> parts.any { it.contains(token) } }) return@mapNotNull null
            val matches = tokens.sumOf { token -> parts.sumOf { count(it, token) } }
            val turn = firstTurn(entry, tokens, scope)
            ChatSearchHit(entry.session, turn?.first, turn?.let { snippet(it.second, tokens.first()) }, matches)
        }.sortedByDescending { it.matches }.take(limit)
    }

    private fun parts(entry: Entry, scope: SearchScope): List<String> = buildList {
        if (scope == SearchScope.ALL || scope == SearchScope.TITLES) add(entry.titleLower)
        if (scope == SearchScope.ALL || scope == SearchScope.MESSAGES) entry.turns.forEach { add(it.userLower); add(it.assistantLower) }
        if (scope == SearchScope.ALL || scope == SearchScope.FILES) entry.turns.forEach { add(it.filesLower) }
    }

    /** The first exchange that holds a searched word, with the text of the field it was found in. */
    private fun firstTurn(entry: Entry, tokens: List<String>, scope: SearchScope): Pair<Int, String>? {
        if (scope == SearchScope.TITLES) return null
        val first = tokens.first()
        entry.turns.forEachIndexed { index, turn ->
            val fields = buildList {
                if (scope != SearchScope.FILES) { add(turn.user to turn.userLower); add(turn.assistant to turn.assistantLower) }
                if (scope != SearchScope.MESSAGES) add(turn.files to turn.filesLower)
            }
            fields.firstOrNull { it.second.contains(first) }?.let { return index to it.first }
        }
        // The first word may sit in the title only; any other word found in a turn still gives a place to jump to.
        entry.turns.forEachIndexed { index, turn ->
            val fields = listOf(turn.user to turn.userLower, turn.assistant to turn.assistantLower)
            fields.firstOrNull { field -> tokens.any { field.second.contains(it) } }?.let { return index to it.first }
        }
        return null
    }

    private fun snippet(text: String, token: String): String? {
        val at = text.lowercase().indexOf(token)
        if (at < 0) return null
        val start = (at - 40).coerceIn(0, text.length)
        val end = (at + 110).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end).replace(MARKDOWN_MARKS, "").replace(WHITESPACE, " ").trim() +
            (if (end < text.length) "…" else "")
    }

    private fun count(haystack: String, token: String): Int {
        var found = 0
        var index = haystack.indexOf(token)
        while (index >= 0) {
            found++
            index = haystack.indexOf(token, index + token.length)
        }
        return found
    }

    companion object {
        const val MAX_RESULTS = 50

        /** Markdown the model writes (bold, code, headings) that would only look like stray symbols in a snippet. */
        private val MARKDOWN_MARKS = Regex("\\*\\*|__|`|(?m)^#{1,6}\\s")
        private val WHITESPACE = Regex("\\s+")
    }
}
