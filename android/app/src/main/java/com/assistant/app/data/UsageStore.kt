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
    val toolCalls: Int = 0,
    /** Tokens by what they were spent on (see [PURPOSE_CHAT]); already part of [inputTokens] and [outputTokens]. */
    val purposes: Map<String, Long> = emptyMap(),
    /** Typed-chat and helper tokens by the model that spent them (model id -> tokens); part of [inputTokens] and [outputTokens]. */
    val models: Map<String, Long> = emptyMap()
) {
    val tokens: Long get() = inputTokens + outputTokens

    operator fun plus(other: UsageDay) = UsageDay(
        day, chatMessages + other.chatMessages, inputTokens + other.inputTokens, outputTokens + other.outputTokens,
        voiceSessions + other.voiceSessions, voiceSeconds + other.voiceSeconds, toolCalls + other.toolCalls,
        (purposes.keys + other.purposes.keys).associateWith { (purposes[it] ?: 0L) + (other.purposes[it] ?: 0L) },
        (models.keys + other.models.keys).associateWith { (models[it] ?: 0L) + (other.models[it] ?: 0L) }
    )
}

/** Counts how much the app is used, per day, on this phone only. Nothing here is ever sent anywhere. */
class UsageStore(private val context: Context) {

    private val daysKey = stringPreferencesKey("days")

    companion object {
        const val KEEP_DAYS = 120
        const val PURPOSE_CHAT = "chat"
        const val PURPOSE_TITLE = "title"
        const val PURPOSE_PERSONA = "persona"
        const val PURPOSE_PDF = "pdf"
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
                    .put("p", JSONObject().also { p -> it.purposes.forEach { (name, tokens) -> p.put(name, tokens) } })
                    .put("md", JSONObject().also { m -> it.models.forEach { (name, tokens) -> m.put(name, tokens) } })
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
                val spent = o.optJSONObject("p")
                val byModel = o.optJSONObject("md")
                UsageDay(
                    day, o.optInt("m"), o.optLong("i"), o.optLong("o"), o.optInt("vs"), o.optLong("vt"), o.optInt("t"),
                    spent?.keys()?.asSequence()?.associateWith { name -> spent.optLong(name) } ?: emptyMap(),
                    byModel?.keys()?.asSequence()?.associateWith { name -> byModel.optLong(name) } ?: emptyMap()
                )
            }.sortedBy { it.day }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
