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

    // ---------------------------------------------------------------- scheduled actions

    fun schedule(
        context: Context, toolName: String?, argsJson: String?, minutes: Int?, atTime: String?, repeatMinutes: Int?
    ): String {
        val tool = checkTool(toolName) ?: return NOT_AUTOMATABLE
        val args = argsJson?.takeIf { it.isNotBlank() } ?: "{}"
        if (runCatching { JSONObject(args) }.isFailure) return "status=invalid\nArguments must be a JSON object, for example {\"state\":\"on\"}."
        val repeat = repeatMinutes ?: 0
        if (repeat != 0 && repeat !in 15..10_080) return "status=invalid\nRepeat must be between 15 and 10080 minutes."
        val now = System.currentTimeMillis()
        val at = when {
            minutes != null -> if (minutes in 1..10_080) now + minutes * 60_000L else return "status=invalid\nMinutes must be 1-10080."
            atTime != null -> {
                val time = runCatching { LocalTime.parse(atTime) }.getOrNull() ?: return "status=invalid\nUse at_time as HH:MM."
                var target = java.time.LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (target <= now) target += 86_400_000L
                target
            }
            else -> return "status=invalid\nGive either minutes or at_time."
        }
        val store = AutomationStore(context)
        val id = store.nextScheduleId()
        val action = ScheduledAction(id, at, tool.functionName, args, repeat)
        store.saveSchedule(action)
        setAlarm(context, action)
        return "status=ok\nScheduled #$id: ${tool.functionName} at ${format(at)}" +
            (if (repeat > 0) ", repeating every $repeat minutes" else "") + ". The time can be off by a few minutes."
    }

    fun listScheduled(context: Context): String {
        val items = AutomationStore(context).schedules().sortedBy { it.atMillis }
        if (items.isEmpty()) return "status=empty\nNothing is scheduled."
        return "status=ok\n" + items.joinToString("\n") {
            "#${it.id}: ${it.toolName} ${it.argsJson} at ${format(it.atMillis)}" +
                if (it.repeatMinutes > 0) ", every ${it.repeatMinutes} min" else ""
        }
    }

    fun cancelScheduled(context: Context, id: Int?): String {
        if (id == null) return "status=invalid\nA required value was missing."
        if (!AutomationStore(context).removeSchedule(id)) return "status=not_found\nNo scheduled action #$id."
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
        return "status=ok\nScheduled action #$id cancelled."
    }

    /** Changes the delay and/or text of a waiting reminder, keeping its number. */
    fun modifyReminder(context: Context, id: Int?, minutes: Int?, message: String?): String {
        if (id == null || (minutes == null && message == null)) return "status=invalid\nGive the reminder number and a new time or message."
        if (minutes != null && minutes !in 1..1440) return "status=invalid\nMinutes must be 1-1440."
        val store = AutomationStore(context)
        val item = store.schedules().firstOrNull { it.id == id } ?: return "status=not_found\nNo scheduled action #$id."
        if (item.toolName != DeviceTool.SHOW_NOTIFICATION.functionName) return "status=invalid\n#$id is not a reminder."
        val args = JSONObject(item.argsJson)
        if (message != null) args.put("message", message)
        val updated = item.copy(
            atMillis = if (minutes != null) System.currentTimeMillis() + minutes * 60_000L else item.atMillis,
            argsJson = args.toString()
        )
        store.saveSchedule(updated)
        setAlarm(context, updated)
        return "status=ok\nReminder #$id updated, now due at ${format(updated.atMillis)}. The time can be off by a few minutes."
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
                    val next = item.copy(atMillis = System.currentTimeMillis() + item.repeatMinutes * 60_000L)
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
        val allowed = tool in enabled || (tool == DeviceTool.SHOW_NOTIFICATION && DeviceTool.SCHEDULE_REMINDER in enabled)
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

    private fun format(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, hh:mm a", Locale.ENGLISH))

    private val NOT_AUTOMATABLE =
        "status=invalid\nThat tool cannot run in the background. Allowed: ${AUTOMATABLE_TOOL_NAMES.joinToString()}."
}
