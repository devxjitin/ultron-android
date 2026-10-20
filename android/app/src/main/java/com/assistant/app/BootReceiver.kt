package com.assistant.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.assistant.app.data.AutomationEngine

/** Alarms do not survive a reboot, so re-arm the saved ones and bring the rule service back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AutomationEngine.rescheduleAll(context)
        if (AutomationEngine.hasRules(context)) runCatching { AppForegroundService.start(context) }
    }
}
