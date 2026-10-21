package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.memoryDataStore by preferencesDataStore(name = "assistant_memory")

/** One lasting fact the assistant remembers about the user, e.g. "Likes strong tea without sugar". */
data class MemoryItem(val id: String, val text: String, val createdAtMillis: Long)

const val MEMORY_SAVE_TOOL = "save_memory"
const val MEMORY_FORGET_TOOL = "forget_memory"
const val MAX_MEMORY_ITEMS = 150
const val MAX_MEMORY_TEXT_CHARS = 280
/** Most characters of memory placed in a session's system instruction (oldest facts are dropped first). */
const val MEMORY_PROMPT_BUDGET_CHARS = 4_000

/** Keeps the assistant's personal memory on this device, so it survives sessions and restarts. */
class MemoryStore(private val context: Context) {

    private val itemsKey = stringPreferencesKey("items")

    val items: Flow<List<MemoryItem>> = context.memoryDataStore.data.map { decode(it[itemsKey]) }

    suspend fun current(): List<MemoryItem> = items.first()

    /** Saves a fact. Returns false when it is blank or already remembered. */
    suspend fun add(text: String): Boolean {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_MEMORY_TEXT_CHARS)
        if (clean.isBlank()) return false
        var added = false
        context.memoryDataStore.edit { prefs ->
            val existing = decode(prefs[itemsKey])
            if (existing.any { it.text.equals(clean, ignoreCase = true) }) return@edit
            val updated = (existing + MemoryItem(UUID.randomUUID().toString(), clean, System.currentTimeMillis()))
                .takeLast(MAX_MEMORY_ITEMS)
            prefs[itemsKey] = encode(updated)
            added = true
        }
        return added
    }

    /**
     * Saves a fact, first removing the older notes it replaces. [replaces] is the old note (or its key
     * words) when the new fact updates or contradicts it. Notes that merely overlap with the new fact are
     * returned as [SaveResult.related] so the caller can decide whether any of them is now outdated.
     */
    suspend fun save(fact: String, replaces: String?): SaveResult {
        val clean = fact.trim().replace(Regex("\\s+"), " ").take(MAX_MEMORY_TEXT_CHARS)
        if (clean.isBlank()) return SaveResult(added = false, replaced = emptyList(), related = emptyList())
        val replaced = ArrayList<String>()
        var added = false
        var related = emptyList<String>()
        var ambiguous = emptyList<String>()
        context.memoryDataStore.edit { prefs ->
            var existing = decode(prefs[itemsKey])
            val choice = chooseReplacement(existing, replaces.orEmpty())
            choice.remove?.let { gone ->
                replaced += gone.text
                existing = existing.filterNot { it.id == gone.id }
            }
            if (choice.ambiguous.isNotEmpty()) ambiguous = choice.ambiguous.map { it.text }.take(3)
            if (existing.none { it.text.equals(clean, ignoreCase = true) }) {
                existing = (existing + MemoryItem(UUID.randomUUID().toString(), clean, System.currentTimeMillis()))
                    .takeLast(MAX_MEMORY_ITEMS)
                added = true
            }
            val newWords = contentWords(clean)
            related = existing.filter { !it.text.equals(clean, ignoreCase = true) }
                .filter { newWords.intersect(contentWords(it.text)).size >= 2 }.map { it.text }.take(3)
            prefs[itemsKey] = encode(existing)
        }
        return SaveResult(added, replaced, related, ambiguous)
    }

    suspend fun update(id: String, text: String) {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_MEMORY_TEXT_CHARS)
        if (clean.isBlank()) return
        context.memoryDataStore.edit { prefs ->
            prefs[itemsKey] = encode(decode(prefs[itemsKey]).map { if (it.id == id) it.copy(text = clean) else it })
        }
    }

    suspend fun remove(id: String) {
        context.memoryDataStore.edit { prefs ->
            prefs[itemsKey] = encode(decode(prefs[itemsKey]).filterNot { it.id == id })
        }
    }

    /** Removes every fact that contains all words of [query] as whole words; returns how many were removed. */
    suspend fun removeMatching(query: String): Int {
        val words = splitWords(query).filter { it.length > 1 }
        if (words.isEmpty()) return 0
        var removed = 0
        context.memoryDataStore.edit { prefs ->
            val existing = decode(prefs[itemsKey])
            val kept = existing.filterNot { item -> splitWords(item.text).toSet().let { itemWords -> words.all { it in itemWords } } }
            removed = existing.size - kept.size
            if (removed > 0) prefs[itemsKey] = encode(kept)
        }
        return removed
    }

    suspend fun clear() {
        context.memoryDataStore.edit { it.remove(itemsKey) }
    }

    private fun encode(items: List<MemoryItem>): String = JSONArray().also { array ->
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("text", it.text).put("ts", it.createdAtMillis))
        }
    }.toString()

    private fun decode(raw: String?): List<MemoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                MemoryItem(obj.getString("id"), obj.getString("text"), obj.optLong("ts"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** [ambiguous] lists older notes that matched the replace hint equally well, so none of them was deleted. */
private fun contentWords(text: String): Set<String> =
    splitWords(text).filter { it.length > 2 && it !in FILLER_WORDS }.toSet()

/** Share of the smaller word set that also appears in the other. */
private fun overlap(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    return a.intersect(b).size.toDouble() / minOf(a.size, b.size)
}

/** What a replace hint does to the saved notes: [remove] is the one clear match; [ambiguous] lists equal candidates that are all kept. */
internal class ReplaceChoice(val remove: MemoryItem?, val ambiguous: List<MemoryItem>)

/** Deletes only a single clearly best match; several equally good matches are left for the model to choose between. */
internal fun chooseReplacement(existing: List<MemoryItem>, hint: String): ReplaceChoice {
    val hintWords = contentWords(hint)
    if (hintWords.isEmpty()) return ReplaceChoice(null, emptyList())
    val scored = existing.map { it to overlap(hintWords, contentWords(it.text)) }.filter { it.second >= REPLACE_OVERLAP }
    val best = scored.maxOfOrNull { it.second } ?: 0.0
    val top = scored.filter { it.second == best }
    return if (top.size == 1 && best >= REPLACE_CLEAR_MATCH) ReplaceChoice(top[0].first, emptyList())
    else ReplaceChoice(null, scored.map { it.first })
}

data class SaveResult(val added: Boolean, val replaced: List<String>, val related: List<String>, val ambiguous: List<String> = emptyList())

private const val REPLACE_OVERLAP = 0.5
private const val REPLACE_CLEAR_MATCH = 0.8

/** Lower-cased words of [text]; letters, digits and combining marks count, so Hindi (Devanagari) works too. */
private fun splitWords(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
private val FILLER_WORDS = setOf("the", "and", "with", "for", "that", "this", "his", "her", "has", "have", "are", "was")

/** The facts that fit the prompt budget, newest kept first, returned oldest to newest. */
fun memoriesForPrompt(items: List<MemoryItem>): List<String> {
    var used = 0
    val kept = ArrayList<String>()
    for (item in items.asReversed()) {
        if (used + item.text.length > MEMORY_PROMPT_BUDGET_CHARS) break
        used += item.text.length
        kept += item.text
    }
    return kept.asReversed()
}
