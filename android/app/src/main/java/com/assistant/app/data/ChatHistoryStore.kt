package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.chatHistoryDataStore by preferencesDataStore(name = "assistant_chat_history")

/**
 * Long-lived, browsable chat history (one entry per session). Separate from [ConversationStore],
 * which only keeps the last few turns as model context and is wiped on every stop -- this one is
 * only ever removed by an explicit delete. Capped so it can't grow unbounded.
 */
class ChatHistoryStore(private val context: Context) {

    private val sessionsKey = stringPreferencesKey("sessions")
    private val quarantineKey = stringPreferencesKey("sessions_quarantine")

    companion object {
        const val MAX_SESSIONS = 50
        const val MAX_TURNS_PER_SESSION = 200
        private const val MAX_QUARANTINED = 20
    }

    /**
     * False when unreadable history was found and set aside. Callers must not treat the visible list as
     * complete then (for example to delete saved files of "missing" chats).
     */
    suspend fun isReadable(): Boolean {
        val prefs = context.chatHistoryDataStore.data.first()
        return decodeAll(prefs[sessionsKey]).bad.isEmpty() && prefs[quarantineKey].isNullOrBlank()
    }

    /** Reads the sessions for a write; anything unreadable is kept in the quarantine key instead of being overwritten. */
    private fun load(prefs: MutablePreferences): List<ChatSession> {
        val decoded = decodeAll(prefs[sessionsKey])
        if (decoded.bad.isNotEmpty()) {
            val kept = runCatching { JSONArray(prefs[quarantineKey].orEmpty().ifBlank { "[]" }) }.getOrDefault(JSONArray())
            decoded.bad.forEach { kept.put(it) }
            val trimmed = JSONArray()
            for (i in maxOf(0, kept.length() - MAX_QUARANTINED) until kept.length()) trimmed.put(kept.get(i))
            prefs[quarantineKey] = trimmed.toString()
        }
        return decoded.sessions
    }

    /** Newest session first. */
    val sessions: Flow<List<ChatSession>> =
        context.chatHistoryDataStore.data.map { prefs -> decode(prefs[sessionsKey]).asReversed() }

    suspend fun appendTurn(sessionId: String, startedAtMillis: Long, turn: ConversationTurn) {
        context.chatHistoryDataStore.edit { prefs ->
            val all = load(prefs).toMutableList()
            val index = all.indexOfFirst { it.id == sessionId }
            if (index >= 0) {
                val existing = all[index]
                // A chat that is being used moves to the newest end, so the cap drops the least recently used one.
                all.removeAt(index)
                all.add(existing.copy(turns = (existing.turns + turn).takeLast(MAX_TURNS_PER_SESSION)))
            } else {
                all.add(ChatSession(sessionId, startedAtMillis, listOf(turn)))
            }
            prefs[sessionsKey] = encode(all.takeLast(MAX_SESSIONS))
        }
    }

    /** Keeps only the first [keep] turns of a chat, used when an earlier message is edited and the rest is replaced. */
    suspend fun truncateTurns(sessionId: String, keep: Int) {
        context.chatHistoryDataStore.edit { prefs ->
            val all = load(prefs)
            if (all.none { it.id == sessionId }) return@edit
            prefs[sessionsKey] = encode(all.map { if (it.id == sessionId) it.copy(turns = it.turns.take(keep)) else it })
        }
    }

    /** Forgets one saved file of a chat (the file itself is removed by the caller). */
    suspend fun removeAttachment(sessionId: String, path: String) {
        context.chatHistoryDataStore.edit { prefs ->
            prefs[sessionsKey] = encode(load(prefs).map { session ->
                if (session.id != sessionId) session
                else session.copy(turns = session.turns.map { turn -> turn.copy(attachments = turn.attachments.filterNot { it.path == path }) })
            })
        }
    }

    /** Forgets every saved file in every chat; the chats and their text stay. */
    suspend fun clearAllAttachments() {
        context.chatHistoryDataStore.edit { prefs ->
            prefs[sessionsKey] = encode(load(prefs).map { session ->
                session.copy(turns = session.turns.map { it.copy(attachments = emptyList()) })
            })
        }
    }

    /** Stores the model-written title; a deleted chat is left alone. */
    suspend fun setTitle(sessionId: String, title: String) {
        context.chatHistoryDataStore.edit { prefs ->
            val all = load(prefs)
            if (all.none { it.id == sessionId }) return@edit
            prefs[sessionsKey] = encode(all.map { if (it.id == sessionId) it.copy(customTitle = title) else it })
        }
    }

    suspend fun delete(sessionId: String) {
        context.chatHistoryDataStore.edit { prefs ->
            prefs[sessionsKey] = encode(load(prefs).filterNot { it.id == sessionId })
        }
    }

    private fun encode(sessions: List<ChatSession>): String {
        val array = JSONArray()
        sessions.forEach { session ->
            val turns = JSONArray()
            session.turns.forEach { turn ->
                turns.put(
                    JSONObject()
                        .put("user", turn.userText)
                        .put("assistant", turn.assistantText)
                        .put("ts", turn.timestampMillis)
                        .put("files", JSONArray().also { files ->
                            turn.attachments.forEach {
                                files.put(JSONObject().put("name", it.name).put("mime", it.mimeType).put("path", it.path).put("size", it.size))
                            }
                        })
                )
            }
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("start", session.startedAtMillis)
                    .put("title", session.customTitle ?: "")
                    .put("turns", turns)
            )
        }
        return array.toString()
    }

    private class Decoded(val sessions: List<ChatSession>, val bad: List<String>)

    private fun decode(raw: String?): List<ChatSession> = decodeAll(raw).sessions

    /** Healthy sessions plus the raw text of anything that could not be read (a whole bad payload counts as one). */
    private fun decodeAll(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), emptyList())
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return Decoded(emptyList(), listOf(raw))
        }
        val sessions = ArrayList<ChatSession>()
        val bad = ArrayList<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            try {
                sessions += decodeSession(array.getJSONObject(i))
            } catch (e: Exception) {
                bad += item.toString()
            }
        }
        return Decoded(sessions, bad)
    }

    private fun decodeSession(obj: JSONObject): ChatSession {
        val turnsArray = obj.getJSONArray("turns")
        return ChatSession(
            id = obj.getString("id"),
            startedAtMillis = obj.optLong("start"),
            customTitle = obj.optString("title").takeIf { it.isNotBlank() },
            turns = (0 until turnsArray.length()).map { j ->
                val t = turnsArray.getJSONObject(j)
                ConversationTurn(
                    userText = t.optString("user"),
                    assistantText = t.optString("assistant"),
                    timestampMillis = t.optLong("ts"),
                    attachments = t.optJSONArray("files")?.let { files ->
                        (0 until files.length()).mapNotNull { k ->
                            val f = files.optJSONObject(k) ?: return@mapNotNull null
                            AttachmentRef(f.optString("name"), f.optString("mime"), f.optString("path"), f.optInt("size"))
                        }
                    }.orEmpty()
                )
            }
        )
    }
}
