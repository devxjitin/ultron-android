package com.assistant.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.assistant.app.AlarmRingService
import java.util.concurrent.atomic.AtomicInteger

/** Posts the assistant's own alert notifications (tool notifications and timed reminders) and starts alarms ringing. */
object AlertNotifications {
    private const val CHANNEL_ID = "assistant_alerts"

    // A channel's importance cannot be raised once it exists, so reminders get a channel of their own.
    private const val REMINDER_CHANNEL_ID = "assistant_reminders"
    private const val ALARM_CHANNEL_ID = "assistant_alarms"
    private val REMINDER_VIBRATION = longArrayOf(0, 500, 250, 500, 250, 500)
    private val nextId = AtomicInteger(2000)

    /**
     * Returns false when nothing could be shown.
     * An alarm or timer ([kind]) rings through [AlarmRingService] until it is stopped; if Android refuses to start that,
     * it falls back to a notification. An [urgent] alert (a reminder) pops over the screen, makes a sound and vibrates.
     */
    fun show(context: Context, title: String, message: String, urgent: Boolean = false, kind: String? = null, label: String = ""): Boolean {
        val ringing = kind == OwnClock.KIND_ALARM || kind == OwnClock.KIND_TIMER
        if (ringing && AlarmRingService.start(context, kind!!, label)) return true
        return post(context, title, message, urgent, kind)
    }

    /** Posts the notification itself. Returns false when notifications are switched off for the app. */
    fun post(context: Context, title: String, message: String, urgent: Boolean, kind: String?): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val ringing = kind == OwnClock.KIND_ALARM || kind == OwnClock.KIND_TIMER
        val channel = if (ringing) {
            NotificationChannel(ALARM_CHANNEL_ID, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = REMINDER_VIBRATION
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
        } else if (urgent) {
            NotificationChannel(REMINDER_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = REMINDER_VIBRATION
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        } else {
            NotificationChannel(CHANNEL_ID, "Assistant alerts", NotificationManager.IMPORTANCE_DEFAULT)
        }
        manager.createNotificationChannel(channel)
        val builder = Notification.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(60))
            .setContentText(message.take(200))
            .setStyle(Notification.BigTextStyle().bigText(message.take(200)))
            .setAutoCancel(true)
        if (ringing) builder.setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
        else if (urgent) builder.setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC)
        val notification = builder.build()
        // Without the ringing service, the notification itself keeps sounding until it is dismissed.
        if (ringing) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(nextId.getAndIncrement(), notification)
        return true
    }
}
