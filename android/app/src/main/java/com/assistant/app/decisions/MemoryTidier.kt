package com.assistant.app.decisions

import com.assistant.app.data.MemoryItem
import com.assistant.app.data.MemoryStore

/**
 * After a new note is saved, asks Decisions (in one call) whether any older note says the same thing or is now
 * contradicted, and removes those older notes. Removed notes stay in the restorable log. Runs off the voice path and
 * does nothing when the call fails, so memory behaves exactly as before.
 */
class MemoryTidier(
    private val client: DecisionsClient,
    private val timeoutMs: Long = 6_000,
) {
    companion object {
        /** Removing a note is destructive, so only act on a clear answer. */
        const val MIN_PROBABILITY = 0.85
        const val MIN_CONFIDENCE = 0.85
        private const val NOTE_CHARS = 280
    }

    /** The ids of [stored] notes that [newNote] duplicates or contradicts. Empty when unsure or when the call failed. */
    suspend fun outdatedIds(newNote: String, stored: List<MemoryItem>): Set<String> {
        val others = stored.filterNot { it.text.equals(newNote, ignoreCase = true) }.take(150)
        if (others.isEmpty()) return emptySet()
        val set = questions {
            others.forEachIndexed { i, note ->
                val stated = "Stored note: \"${note.text.take(NOTE_CHARS)}\""
                yesNo("dup_$i", "$stated\nDoes the new note say the same thing as the stored note, with nothing important added?")
                yesNo("con_$i", "$stated\nDoes the new note replace the stored note because they cannot both be true now (for example a new city, job or preference)?")
            }
        }
        val outcome = client.decide("memory_tidy", listOf(DecisionInput.Text("New note: \"${newNote.take(NOTE_CHARS)}\"")), set, timeoutMs, useCache = false)
        val result = (outcome as? DecisionOutcome.Answered)?.result ?: return emptySet()
        return others.withIndex().filter { (i, _) ->
            listOf("dup_$i", "con_$i").any { key ->
                result.yesNo(key)?.let { it.probability >= MIN_PROBABILITY && it.confidence >= MIN_CONFIDENCE } == true
            }
        }.map { it.value.id }.toSet()
    }

    /** Looks for outdated notes and removes them; returns what was removed. */
    suspend fun tidy(store: MemoryStore, newNote: String): List<MemoryItem> {
        val ids = outdatedIds(newNote, store.current())
        return if (ids.isEmpty()) emptyList() else store.removeOutdated(ids)
    }
}
