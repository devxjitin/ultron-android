package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate

private val Context.usageDataStore by preferencesDataStore(name = "assistant_usage")

/** What the assistant did on one calendar day ([day] is an ISO date such as 2026-10-20). */
data class UsageDay(
    val day: String,
    val chatMessages: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val voiceSessions: Int = 0,
    val voiceSeconds: Long = 0,
    val toolCalls: Int = 0
) {
    val tokens: Long get() = inputTokens + outputTokens

    operator fun plus(other: UsageDay) = UsageDay(
        day, chatMessages + other.chatMessages, inputTokens + other.inputTokens, outputTokens + other.outputTokens,
        voiceSessions + other.voiceSessions, voiceSeconds + other.voiceSeconds, toolCalls + other.toolCalls
    )
}

/** Counts how much the app is used, per day, on this phone only. Nothing here is ever sent anywhere. */
class UsageStore(private val context: Context) {

    private val daysKey = stringPreferencesKey("days")

    companion object {
        const val KEEP_DAYS = 120
    }

    /** Oldest day first. */
    val days: Flow<List<UsageDay>> = context.usageDataStore.data.map { decode(it[daysKey]) }

    suspend fun add(delta: UsageDay) {
        context.usageDataStore.edit { prefs ->
            val all = decode(prefs[daysKey]).associateBy { it.day }.toMutableMap()
            val today = LocalDate.now().toString()
            all[today] = (all[today] ?: UsageDay(today)) + delta
            prefs[daysKey] = encode(all.values.sortedBy { it.day }.takeLast(KEEP_DAYS))
        }
    }

    suspend fun clear() {
        context.usageDataStore.edit { it.remove(daysKey) }
    }

    private fun encode(days: List<UsageDay>): String {
        val root = JSONObject()
        days.forEach {
            root.put(
                it.day, JSONObject().put("m", it.chatMessages).put("i", it.inputTokens).put("o", it.outputTokens)
                    .put("vs", it.voiceSessions).put("vt", it.voiceSeconds).put("t", it.toolCalls)
            )
        }
        return root.toString()
    }

    private fun decode(raw: String?): List<UsageDay> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(raw)
            root.keys().asSequence().map { day ->
                val o = root.getJSONObject(day)
                UsageDay(day, o.optInt("m"), o.optLong("i"), o.optLong("o"), o.optInt("vs"), o.optLong("vt"), o.optInt("t"))
            }.sortedBy { it.day }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
