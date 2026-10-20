package com.assistant.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.assistant.app.data.AutomationEngine

/** Runs a scheduled action when its alarm fires. */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val pending = goAsync()
        AutomationEngine.runScheduled(context.applicationContext, id) { pending.finish() }
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
