package com.assistant.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A tool call the assistant scheduled to run later (once, or repeating every [repeatMinutes]). */
data class ScheduledAction(
    val id: Int,
    val atMillis: Long,
    val toolName: String,
    val argsJson: String,
    val repeatMinutes: Int
)

/** A tool call that runs whenever [event] happens on the phone, for example the charger being plugged in. */
data class AutomationRule(val id: Int, val event: String, val toolName: String, val argsJson: String)

/**
 * Persists scheduled actions and event rules in two small JSON files in the app's private storage.
 * Callers create a new instance each time (receivers, tools), so the lock is process-wide and writes
 * go through [AtomicFile]: a crash mid-write keeps the previous file instead of losing everything.
 */
class AutomationStore(context: Context) {
    private val schedulesFile = File(context.filesDir, "automation_schedules.json")
    private val rulesFile = File(context.filesDir, "automation_rules.json")

    fun schedules(): List<ScheduledAction> = synchronized(LOCK) { readSchedules() }

    fun saveSchedule(action: ScheduledAction) = synchronized(LOCK) {
        writeSchedules(readSchedules().filterNot { it.id == action.id } + action)
    }

    fun removeSchedule(id: Int): Boolean = synchronized(LOCK) {
        val all = readSchedules()
        if (all.none { it.id == id }) return false
        writeSchedules(all.filterNot { it.id == id })
        true
    }

    fun nextScheduleId(): Int = synchronized(LOCK) { (readSchedules().maxOfOrNull { it.id } ?: 0) + 1 }

    fun rules(): List<AutomationRule> = synchronized(LOCK) { readRules() }

    fun addRule(event: String, toolName: String, argsJson: String): Int = synchronized(LOCK) {
        val all = readRules()
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        writeRules(all + AutomationRule(id, event, toolName, argsJson))
        id
    }

    fun removeRule(id: Int): Boolean = synchronized(LOCK) {
        val all = readRules()
        if (all.none { it.id == id }) return false
        writeRules(all.filterNot { it.id == id })
        true
    }

    private fun readSchedules() = read(schedulesFile) {
        ScheduledAction(it.getInt("id"), it.getLong("at"), it.getString("tool"), it.getString("args"), it.optInt("repeat", 0))
    }

    private fun writeSchedules(items: List<ScheduledAction>) = write(schedulesFile, items) {
        JSONObject().put("id", it.id).put("at", it.atMillis).put("tool", it.toolName).put("args", it.argsJson).put("repeat", it.repeatMinutes)
    }

    private fun readRules() = read(rulesFile) {
        AutomationRule(it.getInt("id"), it.getString("event"), it.getString("tool"), it.getString("args"))
    }

    private fun writeRules(items: List<AutomationRule>) = write(rulesFile, items) {
        JSONObject().put("id", it.id).put("event", it.event).put("tool", it.toolName).put("args", it.argsJson)
    }

    private fun <T> read(file: File, parse: (JSONObject) -> T): List<T> = try {
        val array = JSONArray(if (file.exists()) AtomicFile(file).readFully().toString(Charsets.UTF_8) else "[]")
        (0 until array.length()).map { parse(array.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun <T> write(file: File, items: List<T>, toJson: (T) -> JSONObject) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
