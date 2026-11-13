package com.assistant.app.data

import android.content.Context
import com.assistant.app.AlarmRingService
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The assistant's own alarms and timers. The phone's clock app cannot be asked to list or delete its alarms or
 * timers, so these are scheduled by the app itself (the same exact, reboot-proof alarms that reminders use). That
 * lets the assistant list, cancel and change them, and know when one has already rung.
 */
object OwnClock {
    const val KIND_ALARM = "alarm"
    const val KIND_TIMER = "timer"
    private const val MAX_TIMER_SECONDS = 24 * 3600

    /** One saved alarm or timer, read back from the scheduled actions. */
    data class Item(val id: Int, val kind: String, val atMillis: Long, val label: String, val repeatMinutes: Int)

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String, repeat: String?): String {
        if (hour !in 0..23 || minute !in 0..59) return "status=invalid\nHour must be 0-23 and minute 0-59."
        val at = AutomationEngine.reminderTime(null, "%02d:%02d".format(hour, minute), null)
        // Setting the same time again replaces it instead of ringing twice.
        items(context, KIND_ALARM).filter { hourMinute(it.atMillis) == hour to minute }.forEach { remove(context, it.id) }
        val every = AutomationEngine.repeatMinutes(repeat)
        val args = JSONObject().put("title", "Alarm").put("message", label.ifBlank { "%02d:%02d".format(hour, minute) })
            .put("urgent", true).put("kind", KIND_ALARM).put("label", label)
        val result = AutomationEngine.scheduleAt(context, DeviceTool.SHOW_NOTIFICATION, args.toString(), at, every)
        if (!result.startsWith("status=ok")) return result
        val tail = when (every) { 1_440 -> ", every day"; 10_080 -> ", every week"; else -> "" }
        return "status=ok\nAlarm set for ${AutomationEngine.format(at)}$tail."
    }

    fun setTimer(context: Context, seconds: Int, label: String): String {
        if (seconds !in 1..MAX_TIMER_SECONDS) return "status=invalid\nA timer must be 1 second to 24 hours."
        val at = System.currentTimeMillis() + seconds * 1000L
        val args = JSONObject().put("title", "Timer").put("message", label.ifBlank { "Time is up" })
            .put("urgent", true).put("kind", KIND_TIMER).put("label", label)
        val result = AutomationEngine.scheduleAt(context, DeviceTool.SHOW_NOTIFICATION, args.toString(), at, 0)
        if (!result.startsWith("status=ok")) return result
        return "status=ok\nTimer started for ${spoken(seconds)}" + (if (label.isBlank()) "." else " (\"$label\").")
    }

    /** Rings the same alarm again in [minutes], as a single alarm that shows in the list until it rings. */
    fun snooze(context: Context, label: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val args = JSONObject().put("title", "Alarm").put("message", label.ifBlank { clockTime(at) })
            .put("urgent", true).put("kind", KIND_ALARM).put("label", label)
        AutomationEngine.scheduleAt(context, DeviceTool.SHOW_NOTIFICATION, args.toString(), at, 0)
    }

    /** "07:30 AM" for a moment in time, as shown on the ringing screen. */
    fun clockTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH))

    fun list(context: Context, kind: String): String {
        val found = items(context, kind)
        val noun = if (kind == KIND_ALARM) "alarms" else "timers"
        if (found.isEmpty()) return "status=empty\nNo $noun are set."
        return "status=ok\n" + found.joinToString("\n") { line(it) }
    }

    /** Cancels by number, by time (alarms), by name, or the soonest one when nothing says which. */
    fun cancel(context: Context, kind: String, id: Int?, hour: Int?, minute: Int?, label: String?): String {
        val found = items(context, kind)
        val noun = if (kind == KIND_ALARM) "alarm" else "timer"
        // "Stop the alarm" while one is ringing means the sound, not the next alarm on the list.
        val ringing = AlarmRingService.current.value
        if (ringing != null && ringing.kind == kind && id == null && hour == null && label.isNullOrBlank()) {
            AlarmRingService.stop(context)
            return "status=ok\nStopped the ringing $noun."
        }
        val matches = when {
            id != null -> found.filter { it.id == id }
            hour != null -> found.filter { hourMinute(it.atMillis) == hour to (minute ?: 0) }
            !label.isNullOrBlank() -> found.filter { it.label.contains(label, ignoreCase = true) }
            else -> listOfNotNull(found.firstOrNull())
        }
        if (matches.isEmpty()) {
            return "status=not_found\nNo matching $noun is set. " + if (found.isEmpty()) "None are set at all." else "Set: " + found.joinToString("; ") { line(it) }
        }
        matches.forEach { remove(context, it.id) }
        return "status=ok\nCancelled ${matches.joinToString("; ") { line(it) }}. It is really gone; no need to open the clock app."
    }

    fun modifyAlarm(context: Context, hour: Int, minute: Int, newHour: Int, newMinute: Int, label: String?): String {
        val old = items(context, KIND_ALARM).firstOrNull { hourMinute(it.atMillis) == hour to minute }
            ?: return "status=not_found\nNo alarm is set for %02d:%02d.".format(hour, minute)
        val result = setAlarm(context, newHour, newMinute, label ?: old.label, repeatName(old.repeatMinutes))
        if (result.startsWith("status=ok") && (hour to minute) != (newHour to newMinute)) remove(context, old.id)
        return result
    }

    fun items(context: Context, kind: String): List<Item> = AutomationStore(context).schedules().mapNotNull { action ->
        if (action.toolName != DeviceTool.SHOW_NOTIFICATION.functionName) return@mapNotNull null
        val args = runCatching { JSONObject(action.argsJson) }.getOrNull() ?: return@mapNotNull null
        if (args.optString("kind") != kind) return@mapNotNull null
        Item(action.id, kind, action.atMillis, args.optString("label"), action.repeatMinutes)
    }.sortedBy { it.atMillis }

    private fun remove(context: Context, id: Int) {
        AutomationEngine.cancelScheduled(context, id)
    }

    private fun line(item: Item): String {
        val name = if (item.label.isBlank()) "" else " \"${item.label}\""
        val every = when (item.repeatMinutes) { 0 -> ""; 1_440 -> ", every day"; 10_080 -> ", every week"; else -> "" }
        return if (item.kind == KIND_TIMER) {
            "timer$name rings in ${spoken(((item.atMillis - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0))}"
        } else "alarm$name at ${AutomationEngine.format(item.atMillis)}$every"
    }

    private fun repeatName(minutes: Int) = when (minutes) { 1_440 -> "daily"; 10_080 -> "weekly"; else -> null }

    private fun hourMinute(millis: Long): Pair<Int, Int> =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour to it.minute }

    internal fun spoken(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        return listOfNotNull(
            if (h > 0) "$h hour${if (h == 1) "" else "s"}" else null,
            if (m > 0) "$m minute${if (m == 1) "" else "s"}" else null,
            if (s > 0 || totalSeconds == 0) "$s second${if (s == 1) "" else "s"}" else null
        ).joinToString(" ")
    }
}
