package com.assistant.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** An alarm the assistant set in the clock app. */
data class ClockAlarm(val hour: Int, val minute: Int, val label: String)

/**
 * Remembers the alarms set through the assistant. The clock app offers no way to read its alarms back, so this
 * list is what lets the assistant show, cancel and change them. Alarms made by hand in the clock app are not here.
 */
class ClockAlarmStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clock_alarms", Context.MODE_PRIVATE)

    fun alarms(): List<ClockAlarm> = synchronized(LOCK) { read().sortedWith(compareBy({ it.hour }, { it.minute })) }

    fun add(alarm: ClockAlarm) = synchronized(LOCK) {
        write(read().filterNot { it.hour == alarm.hour && it.minute == alarm.minute } + alarm)
    }

    fun remove(hour: Int, minute: Int): ClockAlarm? = synchronized(LOCK) {
        val all = read()
        val found = all.firstOrNull { it.hour == hour && it.minute == minute } ?: return null
        write(all - found)
        found
    }

    private fun read(): List<ClockAlarm> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            ClockAlarm(item.getInt("hour"), item.getInt("minute"), item.optString("label"))
        }
    }.getOrDefault(emptyList())

    private fun write(alarms: List<ClockAlarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(JSONObject().put("hour", it.hour).put("minute", it.minute).put("label", it.label)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "alarms"
        val LOCK = Any()
    }
}
