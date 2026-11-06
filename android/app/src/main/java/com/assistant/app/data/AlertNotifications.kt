package com.assistant.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/** Posts the assistant's own alert notifications (tool notifications and timed reminders). */
object AlertNotifications {
    private const val CHANNEL_ID = "assistant_alerts"

    // A channel's importance cannot be raised once it exists, so reminders get a channel of their own.
    private const val REMINDER_CHANNEL_ID = "assistant_reminders"
    private val REMINDER_VIBRATION = longArrayOf(0, 500, 250, 500, 250, 500)
    private val nextId = AtomicInteger(2000)

    /**
     * Returns false when notifications are switched off for the app.
     * An [urgent] alert (a reminder) pops over the screen, makes a sound and vibrates, and stays until it is dismissed.
     */
    fun show(context: Context, title: String, message: String, urgent: Boolean = false): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val channel = if (urgent) {
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
        if (urgent) builder.setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC)
        manager.notify(nextId.getAndIncrement(), builder.build())
        return true
    }
}
