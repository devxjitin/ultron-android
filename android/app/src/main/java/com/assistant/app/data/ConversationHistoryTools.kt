package com.assistant.app.data

import org.json.JSONObject
import java.time.ZoneId

/**
 * Runs the two long-term memory tools and words their results for the voice model. Every result starts with a
 * `status=` line like the other tools. Kept free of Android types so it can be unit tested.
 */
class ConversationHistoryTools(
    private val store: ConversationHistoryStore,
    private val embedQuery: suspend (String) -> FloatArray,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** The session in progress; its exchanges are already in the model's context, so "recent" skips them. */
    private val currentSessionId: String? = null
) {
    companion object {
        const val DEFAULT_RECENT = 6
        const val MAX_RECENT = 20
        const val MAX_QUERY_CHARS = 300
        const val EMPTY_MEMORY = "status=no_match\nNo earlier conversations are saved yet. Say you have nothing from before; do not guess."
    }

    suspend fun run(tool: DeviceTool, args: JSONObject): String = when (tool) {
        DeviceTool.SEARCH_CONVERSATION_HISTORY -> search(args)
        DeviceTool.RECENT_CONVERSATION -> recent(args)
        else -> "status=unknown_tool\nThat is not a memory tool."
    }

    private suspend fun search(args: JSONObject): String {
        val query = args.optString("query").trim().take(MAX_QUERY_CHARS)
        if (query.isEmpty()) return "status=invalid\nGive a query: what to look for in earlier conversations."
        val range = try {
            DateRange.parse(
                args.optString("from_date").takeIf { it.isNotBlank() },
                args.optString("to_date").takeIf { it.isNotBlank() },
                zone
            )
        } catch (e: IllegalArgumentException) {
            return "status=invalid\n${e.message}"
        }
        if (store.count() == 0L) return EMPTY_MEMORY
        val found = ConversationHistorySearch(store, embedQuery, zone).search(query, range)
        return if (found.isEmpty()) {
            "status=no_match\nNothing like that was found in earlier conversations" +
                (if (range.isAll) "" else " in that date range") +
                ". Tell the user you do not have it; do not guess."
        } else {
            "status=ok\nMatching exchanges from earlier conversations (times are the phone's local time):\n$found"
        }
    }

    private fun recent(args: JSONObject): String {
        val count = if (args.has("count")) args.optInt("count", DEFAULT_RECENT) else DEFAULT_RECENT
        val turns = store.recent(count.coerceIn(1, MAX_RECENT), currentSessionId)
        if (turns.isEmpty()) return EMPTY_MEMORY
        return "status=ok\nThe most recent exchanges from earlier sessions, newest last (times are the phone's local time):\n" +
            turns.joinToString("\n") { renderTurn(it, zone) }
    }
}
