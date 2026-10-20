package com.assistant.app

import android.service.notification.NotificationListenerService

/**
 * Exists only so the read-notifications tool can see the active notifications once the user
 * grants notification access in system Settings. It stores nothing and sends nothing by itself.
 */
class AssistantNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    companion object {
        @Volatile var instance: AssistantNotificationListener? = null
    }
}
