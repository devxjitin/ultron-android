package com.assistant.app.data

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import com.assistant.app.AutomationReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Runs tool calls without the voice session: once or repeating at a set time, or when a phone event
 * fires. Only tools that never need a screen are allowed, because Android blocks background apps
 * from opening other apps. Each run posts its result as a notification.
 */
object AutomationEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastEventAt = mutableMapOf<String, Long>()

    val EVENTS = listOf("charger_connected", "charger_disconnected", "battery_low", "headphones_plugged", "screen_unlocked")

    private const val RUN_TIMEOUT_MS = 8_000L
    private const val EVENT_COOLDOWN_MS = 5_000L
    const val MAX_REMINDER_MINUTES = 525_600

    // ---------------------------------------------------------------- scheduled actions

    /** Saves and arms one action for an exact moment. [repeatMinutes] is 0 for a single run. */
    fun scheduleAt(context: Context, tool: DeviceTool, argsJson: String, atMillis: Long, repeatMinutes: Int): String {
        val store = AutomationStore(context)
        val id = store.nextScheduleId()
        val action = ScheduledAction(id, atMillis, tool.functionName, argsJson, repeatMinutes)
        store.saveSchedule(action)
        setAlarm(context, action)
        return "status=ok\nScheduled #$id: ${tool.functionName} at ${format(atMillis)}" +
            (if (repeatMinutes > 0) ", repeating every $repeatMinutes minutes" else "") + "."
    }

    /**
     * When a reminder should go off: [minutes] from [now], or the clock time [atTime] (HH:MM) on [date] (YYYY-MM-DD).
     * Without a date the next occurrence of that time is used, so "9 am" said at 10 pm means tomorrow.
     * Throws [IllegalArgumentException] with a sentence the assistant can read out.
     */
    fun reminderTime(
        minutes: Int?, atTime: String?, date: String?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val at = when {
            minutes != null -> {
                require(minutes in 1..MAX_REMINDER_MINUTES) { "Minutes must be 1-$MAX_REMINDER_MINUTES. For a clock time, give at_time instead." }
                now + minutes * 60_000L
            }
            atTime != null -> {
                val time = runCatching { LocalTime.parse(atTime) }.getOrNull() ?: throw IllegalArgumentException("Use at_time as HH:MM, 24-hour.")
                if (date != null) {
                    val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: throw IllegalArgumentException("Use date as YYYY-MM-DD.")
                    val target = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    require(target > now) { "That time has already passed." }
                    target
                } else {
                    val today = java.time.LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
                    val target = today.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    if (target > now) target else today.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
                }
            }
            else -> throw IllegalArgumentException("Give either minutes or at_time.")
        }
        require(at - now <= MAX_REMINDER_MINUTES * 60_000L) { "A reminder can be at most one year ahead." }
        return at
    }

    /** "daily" and "weekly" as minutes between runs; anything else is a single run. */
    fun repeatMinutes(repeat: String?): Int = when (repeat?.lowercase()) {
        "daily" -> 1_440
        "weekly" -> 10_080
        else -> 0
    }

    fun listScheduled(context: Context): String {
        // Alarms and timers are saved the same way but have their own list, cancel and change tools.
        val items = AutomationStore(context).schedules().filterNot(::isClock).sortedBy { it.atMillis }
        if (items.isEmpty()) return "status=empty\nNo reminders are waiting."
        return "status=ok\n" + items.joinToString("\n") {
            // A reminder reads as its own text; anything else saved by an older build keeps its tool name.
            val what = if (it.toolName == DeviceTool.SHOW_NOTIFICATION.functionName) {
                runCatching { JSONObject(it.argsJson).optString("message") }.getOrDefault("").ifBlank { "Reminder" }
            } else it.toolName
            "#${it.id}: \"$what\" at ${format(it.atMillis)}" +
                when (it.repeatMinutes) { 0 -> ""; 1_440 -> ", every day"; 10_080 -> ", every week"; else -> ", every ${it.repeatMinutes} min" }
        }
    }

    fun cancelScheduled(context: Context, id: Int?): String {
        if (id == null) return "status=invalid\nA required value was missing."
        if (!AutomationStore(context).removeSchedule(id)) return "status=not_found\nNo scheduled action #$id."
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
        return "status=ok\nScheduled action #$id cancelled."
    }

    /** Cancels a reminder by its number. An alarm or timer with that number is left alone. */
    fun cancelReminder(context: Context, id: Int?): String {
        if (id == null) return "status=invalid\nA required value was missing."
        val item = AutomationStore(context).schedules().firstOrNull { it.id == id } ?: return "status=not_found\nNo reminder #$id."
        if (isClock(item)) return "status=invalid\n#$id is an alarm or timer, not a reminder. Use cancel_alarm or cancel_timer for it."
        return cancelScheduled(context, id)
    }

    /** True for one of the assistant's own alarms or timers, which ring instead of only showing a notification. */
    internal fun isClock(action: ScheduledAction): Boolean = clockKind(action.toolName, action.argsJson) != null

    /** "alarm" or "timer" when the saved action is one, else null. */
    internal fun clockKind(toolName: String, argsJson: String): String? {
        if (toolName != DeviceTool.SHOW_NOTIFICATION.functionName) return null
        val kind = runCatching { JSONObject(argsJson).optString("kind") }.getOrDefault("")
        return kind.takeIf { it == OwnClock.KIND_ALARM || it == OwnClock.KIND_TIMER }
    }

    /** Changes the delay and/or text of a waiting reminder, keeping its number. */
    fun modifyReminder(context: Context, id: Int?, minutes: Int?, message: String?, atTime: String? = null, date: String? = null): String {
        val newTime = minutes != null || atTime != null
        if (id == null || (!newTime && message == null)) return "status=invalid\nGive the reminder number and a new time or message."
        val newAt = if (newTime) {
            runCatching { reminderTime(minutes, atTime, date) }.getOrElse { return "status=invalid\n${it.message}" }
        } else null
        val store = AutomationStore(context)
        val item = store.schedules().firstOrNull { it.id == id } ?: return "status=not_found\nNo scheduled action #$id."
        if (item.toolName != DeviceTool.SHOW_NOTIFICATION.functionName) return "status=invalid\n#$id is not a reminder."
        if (isClock(item)) return "status=invalid\n#$id is an alarm or timer, not a reminder. Use modify_alarm, or cancel it and set a new one."
        val args = JSONObject(item.argsJson)
        if (message != null) args.put("message", message)
        val updated = item.copy(
            atMillis = newAt ?: item.atMillis,
            argsJson = args.toString()
        )
        store.saveSchedule(updated)
        setAlarm(context, updated)
        return "status=ok\nReminder #$id updated, now due at ${format(updated.atMillis)}."
    }

    /** Re-arms every saved alarm after a reboot (alarms do not survive one). Missed ones run shortly. */
    fun rescheduleAll(context: Context) {
        val store = AutomationStore(context)
        val now = System.currentTimeMillis()
        store.schedules().forEach {
            val action = if (it.atMillis <= now) it.copy(atMillis = now + 30_000L).also(store::saveSchedule) else it
            setAlarm(context, action)
        }
    }

    fun hasRules(context: Context) = AutomationStore(context).rules().isNotEmpty()

    /** Called by the alarm receiver; [done] must be called when finished so the receiver can end. */
    fun runScheduled(context: Context, id: Int, done: () -> Unit) {
        scope.launch {
            try {
                val store = AutomationStore(context)
                val item = store.schedules().firstOrNull { it.id == id } ?: return@launch
                runAndNotify(context, item.toolName, item.argsJson)
                if (item.repeatMinutes > 0) {
                    // Counted from the planned time, so a daily 9 am reminder stays at 9 am instead of drifting later each day.
                    val step = item.repeatMinutes * 60_000L
                    var nextAt = item.atMillis + step
                    val now = System.currentTimeMillis()
                    if (nextAt <= now) nextAt += ((now - nextAt) / step + 1) * step
                    val next = item.copy(atMillis = nextAt)
                    store.saveSchedule(next)
                    setAlarm(context, next)
                } else {
                    store.removeSchedule(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Scheduled action #$id failed", e)
            } finally {
                done()
            }
        }
    }

    // ---------------------------------------------------------------- event rules

    // No tool offers rule creation or listing right now. The engine stays so rules saved by earlier builds keep
    // running after an update; removing it needs a decision on those saved rules first.

    fun addRule(context: Context, event: String?, toolName: String?, argsJson: String?): String {
        if (event == null || event !in EVENTS) return "status=invalid\nEvent must be one of: ${EVENTS.joinToString()}."
        val tool = checkTool(toolName) ?: return NOT_AUTOMATABLE
        val args = argsJson?.takeIf { it.isNotBlank() } ?: "{}"
        if (runCatching { JSONObject(args) }.isFailure) return "status=invalid\nArguments must be a JSON object, for example {\"state\":\"on\"}."
        val id = AutomationStore(context).addRule(event, tool.functionName, args)
        return "status=ok\nRule #$id: when $event, run ${tool.functionName}. It works while the assistant app is running."
    }

    /** Called when a phone event fires; runs every matching rule. */
    fun onEvent(context: Context, event: String) {
        val now = System.currentTimeMillis()
        synchronized(lastEventAt) {
            if (now - (lastEventAt[event] ?: 0L) < EVENT_COOLDOWN_MS) return
            lastEventAt[event] = now
        }
        scope.launch {
            try {
                AutomationStore(context).rules().filter { it.event == event }.forEach {
                    runAndNotify(context, it.toolName, it.argsJson)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Automation rule for $event failed", e)
            }
        }
    }

    // ---------------------------------------------------------------- shared

    private fun checkTool(name: String?): DeviceTool? =
        name?.let { DeviceTool.fromFunctionName(it) }?.takeIf { it.functionName in AUTOMATABLE_TOOL_NAMES }

    private suspend fun runAndNotify(context: Context, toolName: String, argsJson: String) {
        val app = context.applicationContext as Application
        val tool = DeviceTool.fromFunctionName(toolName) ?: return
        val enabled = AppSettingsStore(app).settings.first().enabledDeviceTools
        // Reminders are stored as notifications, so the reminder tool's own switch is enough to show them.
        val allowed = tool in enabled || (tool == DeviceTool.SHOW_NOTIFICATION &&
            (DeviceTool.SCHEDULE_REMINDER in enabled || DeviceTool.SET_ALARM in enabled || DeviceTool.SET_TIMER in enabled))
        val result = if (!allowed) {
            "status=disabled\nThis tool is switched off in Settings."
        } else {
            try {
                withTimeoutOrNull(RUN_TIMEOUT_MS) {
                    DeviceToolRunner(app, app.getSystemService(AudioManager::class.java), { null }, { }, { })
                        .run(tool, JSONObject(argsJson))
                } ?: "status=failed\nThe action took too long."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AssistantError", "Automation ${tool.functionName} failed", e)
                "status=failed\nThe action could not be completed."
            }
        }
        val body = if (result.startsWith("status=ok")) result.substringAfter("\n", "Done.") else result.replace("\n", ": ")
        // show_notification already posted its own notification; a second "Notification shown" would be noise.
        if (tool == DeviceTool.SHOW_NOTIFICATION && result.startsWith("status=ok")) return
        AlertNotifications.show(app, "Automation: ${tool.label}", body)
    }

    private fun setAlarm(context: Context, action: ScheduledAction) {
        context.getSystemService(AlarmManager::class.java)
            .let { manager ->
                val pending = pendingIntent(context, action.id)
                // An alarm or timer is set the way a clock app sets one: Android wakes the phone for it even in deep sleep
                // and shows the alarm icon. If that is refused, it is armed like a reminder below.
                if (isClock(action)) {
                    val show = PendingIntent.getActivity(
                        context, 0, Intent(context, com.assistant.app.MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val armed = runCatching { manager.setAlarmClock(AlarmManager.AlarmClockInfo(action.atMillis, show), pending) }.isSuccess
                    if (armed) return@let
                }
                // Exact when Android allows it, so a reminder fires at the minute asked for instead of being batched.
                val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
                if (exact) runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, action.atMillis, pending) }
                    .onFailure { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, action.atMillis, pending) }
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, action.atMillis, pending)
            }
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, id, Intent(context, AutomationReceiver::class.java).putExtra(AutomationReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun format(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, hh:mm a", Locale.ENGLISH))

    private val NOT_AUTOMATABLE =
        "status=invalid\nThat tool cannot run in the background. Allowed: ${AUTOMATABLE_TOOL_NAMES.joinToString()}."
}
