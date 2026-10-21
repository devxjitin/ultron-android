package com.assistant.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Int, val text: String, val createdAtMillis: Long)

/** Notes saved by the assistant, kept in one small JSON file in the app's private storage. */
class NoteStore(context: Context) {
    private val file = File(context.filesDir, "notes.json")
    private val lastIdFile = File(context.filesDir, "notes.lastid")

    // Automation and the assistant each create their own store, so the lock is shared by all instances.
    fun add(text: String): Int = synchronized(LOCK) {
        val notes = readAll()
        // Ids are never reused, so a stale reference to a deleted note cannot hit a newer one.
        val lastIssued = runCatching { lastIdFile.readText().trim().toInt() }.getOrDefault(0)
        val id = maxOf(notes.maxOfOrNull { it.id } ?: 0, lastIssued) + 1
        lastIdFile.writeText(id.toString())
        writeAll(notes + Note(id, text.trim().take(MAX_NOTE_CHARS), System.currentTimeMillis()))
        id
    }

    /** Newest first; [query] filters by case-insensitive substring. */
    fun search(query: String?, limit: Int = 10): List<Note> = synchronized(LOCK) {
        readAll().filter { query.isNullOrBlank() || it.text.contains(query, ignoreCase = true) }
            .sortedByDescending { it.createdAtMillis }
            .take(limit)
    }

    fun delete(id: Int): Boolean = synchronized(LOCK) {
        val notes = readAll()
        if (notes.none { it.id == id }) return false
        writeAll(notes.filterNot { it.id == id })
        true
    }

    private fun readAll(): List<Note> = try {
        val array = JSONArray(if (file.exists()) file.readText() else "[]")
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Note(item.getInt("id"), item.getString("text"), item.getLong("at"))
        }
    } catch (e: Exception) {
        // Keep the unreadable file for recovery instead of letting the next save overwrite it.
        runCatching { file.renameTo(File(file.parentFile, "notes.corrupt-${System.currentTimeMillis()}.json")) }
        emptyList()
    }

    private fun writeAll(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.createdAtMillis)) }
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    private companion object {
        val LOCK = Any()
        const val MAX_NOTE_CHARS = 1000
    }
}
