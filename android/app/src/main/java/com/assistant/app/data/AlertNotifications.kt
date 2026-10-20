package com.assistant.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/** Posts the assistant's own alert notifications (tool notifications and timed reminders). */
object AlertNotifications {
    private const val CHANNEL_ID = "assistant_alerts"
    private val nextId = AtomicInteger(2000)

    /** Returns false when notifications are switched off for the app. */
    fun show(context: Context, title: String, message: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Assistant alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.notify(
            nextId.getAndIncrement(),
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title.take(60))
                .setContentText(message.take(200))
                .setAutoCancel(true)
                .build()
        )
        return true
    }
}
