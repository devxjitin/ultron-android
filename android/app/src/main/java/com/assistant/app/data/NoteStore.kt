package com.assistant.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Int, val text: String, val createdAtMillis: Long)

/** Notes saved by the assistant, kept in one small JSON file in the app's private storage. */
class NoteStore(context: Context) {
    private val file = File(context.filesDir, "notes.json")

    @Synchronized
    fun add(text: String): Int {
        val notes = readAll()
        val id = (notes.maxOfOrNull { it.id } ?: 0) + 1
        writeAll(notes + Note(id, text.trim().take(MAX_NOTE_CHARS), System.currentTimeMillis()))
        return id
    }

    /** Newest first; [query] filters by case-insensitive substring. */
    @Synchronized
    fun search(query: String?, limit: Int = 10): List<Note> =
        readAll().filter { query.isNullOrBlank() || it.text.contains(query, ignoreCase = true) }
            .sortedByDescending { it.createdAtMillis }
            .take(limit)

    @Synchronized
    fun delete(id: Int): Boolean {
        val notes = readAll()
        if (notes.none { it.id == id }) return false
        writeAll(notes.filterNot { it.id == id })
        return true
    }

    private fun readAll(): List<Note> = try {
        val array = JSONArray(if (file.exists()) file.readText() else "[]")
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Note(item.getInt("id"), item.getString("text"), item.getLong("at"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeAll(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.createdAtMillis)) }
        file.writeText(array.toString())
    }

    private companion object {
        const val MAX_NOTE_CHARS = 1000
    }
}
