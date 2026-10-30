package com.assistant.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets the read-notifications tool see the active notifications once the user grants notification
 * access in system Settings, and hands each new one to [onNotification] so a running voice session
 * can mention it. It stores nothing and sends nothing by itself.
 */
class AssistantNotificationListener : NotificationListenerService() {
    private var lastKey: String? = null
    private var lastText: String? = null

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val handler = onNotification ?: return
        val n = sbn.notification
        // Ongoing media/timer/service entries, group headers, silent progress bars and Ultron's own are noise.
        if (sbn.packageName == packageName || !sbn.isClearable || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_TRANSPORT) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty().trim()
        if (title.isEmpty() && body.isEmpty()) return
        // Apps re-post the same notification to update it; say it once.
        val text = "$title|$body"
        if (sbn.key == lastKey && text == lastText) return
        lastKey = sbn.key
        lastText = text
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        handler(app, title, body)
    }

    companion object {
        @Volatile var instance: AssistantNotificationListener? = null

        /** Called for each new notification worth mentioning, on a binder thread. */
        @Volatile var onNotification: ((app: String, title: String, text: String) -> Unit)? = null
    }
}
