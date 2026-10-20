package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "assistant_conversation")

/**
 * Persists only the last 5 conversation turns on device, so continuity survives
 * an app restart or a fresh Live session without ever growing unbounded.
 */
class ConversationStore(private val context: Context) {

    private val turnsKey = stringPreferencesKey("recent_turns")

    companion object {
        const val MAX_TURNS = 5
    }

    val recentTurns: Flow<List<ConversationTurn>> =
        context.dataStore.data.map { prefs -> decode(prefs[turnsKey]) }

    suspend fun appendTurn(turn: ConversationTurn) {
        context.dataStore.edit { prefs ->
            val updated = (decode(prefs[turnsKey]) + turn).takeLast(MAX_TURNS)
            prefs[turnsKey] = encode(updated)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs -> prefs.remove(turnsKey) }
    }

    private fun encode(turns: List<ConversationTurn>): String {
        val array = JSONArray()
        turns.forEach { turn ->
            val obj = JSONObject()
            obj.put("user", turn.userText)
            obj.put("assistant", turn.assistantText)
            obj.put("ts", turn.timestampMillis)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<ConversationTurn> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ConversationTurn(
                    userText = obj.optString("user"),
                    assistantText = obj.optString("assistant"),
                    timestampMillis = obj.optLong("ts")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
