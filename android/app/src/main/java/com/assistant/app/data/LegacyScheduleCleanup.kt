package com.assistant.app.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.io.File
import org.json.JSONArray

/**
 * The Schedule feature was removed. Phones that had saved schedules still have their alarms armed and a small file on disk,
 * so the first start after the update cancels the alarms and deletes the file. Nothing is left once this has run.
 */
object LegacyScheduleCleanup {
    private const val FILE_NAME = "scheduled_prompts.json"
    private const val RECEIVER_CLASS = "com.assistant.app.ScheduledPromptReceiver"
    /** Alarm request codes the old feature used, so they never clashed with reminder alarms. */
    private const val REQUEST_CODE_BASE = 700_000

    fun run(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val ids = runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { array.getJSONObject(it).getInt("id") }
        }.getOrDefault(emptyList())
        ids.forEach { id ->
            // The receiver class no longer exists, so the alarm is found by its component name alone.
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE_BASE + id, Intent().setClassName(context, RECEIVER_CLASS),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            manager?.cancel(pending)
            pending.cancel()
        }
        context.getSystemService(NotificationManager::class.java)?.let {
            it.deleteNotificationChannel("scheduled_runs")
            it.deleteNotificationChannel("scheduled_unlock")
        }
        file.delete()
        File(context.filesDir, "$FILE_NAME.new").delete()
        File(context.filesDir, "$FILE_NAME.bak").delete()
    }
}
