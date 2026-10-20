package com.assistant.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.assistant.app.data.AlertNotifications

/** Fires a reminder scheduled by the assistant's schedule_reminder tool. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertNotifications.show(context, "Reminder", intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
