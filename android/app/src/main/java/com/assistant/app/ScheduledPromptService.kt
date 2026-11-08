package com.assistant.app

import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.assistant.app.data.AlertNotifications
import com.assistant.app.data.ApiKeyStore
import com.assistant.app.data.AppSettingsStore
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.DeviceToolRunner
import com.assistant.app.data.LicenseStore
import com.assistant.app.data.ScheduledPrompts
import com.assistant.app.data.isUsableOffline
import com.assistant.app.network.AgentOutcome
import com.assistant.app.network.ScheduledPromptAgent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Starts the run for a scheduled prompt when its alarm fires. */
class ScheduledPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        ScheduledPromptService.start(context, id, advance = true)
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}

/**
 * Runs scheduled prompts in a short foreground service, since a run (Gemini plus opening apps and tapping) can take a
 * minute, far longer than an alarm broadcast may live. [EXTRA_ADVANCE] is false for a run the user started by tapping the
 * "unlock to run" notification or Run now, so a repeating prompt is not moved on a second time.
 */
class ScheduledPromptService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        val advance = intent?.getBooleanExtra(EXTRA_ADVANCE, true) ?: true
        val prompt = if (id >= 0) ScheduledPrompts.get(this, id) else null
        goForeground(prompt?.prompt ?: "Scheduled task")
        if (prompt == null || (advance && !prompt.enabled)) {
            if (running.get() == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        running.incrementAndGet()
        scope.launch {
            try {
                runPrompt(application, id, advance)
            } finally {
                if (running.decrementAndGet() == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RUN_CHANNEL_ID, "Scheduled tasks", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, RUN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Running a scheduled task")
            .setContentText(text.take(80))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(RUN_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(RUN_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ScheduledPrompt"
        const val EXTRA_ID = "id"
        const val EXTRA_ADVANCE = "advance"
        private const val RUN_CHANNEL_ID = "scheduled_runs"
        private const val RUN_NOTIFICATION_ID = 4_210
        private const val UNLOCK_NOTIFICATION_BASE = 4_300
        private const val RUN_TIMEOUT_MS = 4 * 60_000L

        /** Tools that make no sense with nobody watching, or that need the voice session itself. */
        private val NEVER_IN_BACKGROUND = setOf(
            DeviceTool.LOOK_AT_SCREEN, DeviceTool.SHOW_IMAGE, DeviceTool.POWER_MENU, DeviceTool.SPLIT_SCREEN,
            DeviceTool.SEARCH_CONVERSATION_HISTORY, DeviceTool.RECENT_CONVERSATION
        )

        /** Tools that open apps or read and touch the screen, which a locked phone cannot do. */
        private val NEEDS_SCREEN = setOf(
            DeviceTool.PRESS_BACK, DeviceTool.RECENT_APPS, DeviceTool.PREVIOUS_APP, DeviceTool.NOTIFICATION_SHADE,
            DeviceTool.QUICK_SETTINGS, DeviceTool.SCROLL, DeviceTool.HOME_SCREEN, DeviceTool.OPEN_SETTINGS, DeviceTool.OPEN_APP,
            DeviceTool.OPEN_CAMERA, DeviceTool.OPEN_FILE, DeviceTool.OPEN_APP_SETTINGS, DeviceTool.DIRECTIONS,
            DeviceTool.READ_SCREEN, DeviceTool.CURRENT_APP, DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM,
            DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT, DeviceTool.PRESS_ENTER, DeviceTool.WAIT_FOR_TEXT,
            DeviceTool.OPEN_PANEL, DeviceTool.SWIPE, DeviceTool.LONG_PRESS, DeviceTool.OPEN_URL, DeviceTool.SHARE_TEXT
        )

        fun start(context: Context, id: Int, advance: Boolean) {
            val intent = Intent(context, ScheduledPromptService::class.java)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_ADVANCE, advance)
            runCatching { context.startForegroundService(intent) }
                .onFailure {
                    Log.w(TAG, "Could not start the scheduled run", it)
                    // Android refused a background start: record it so the Schedule page explains what happened.
                    if (advance) ScheduledPrompts.finishRun(context, id, "Android did not let Ultron start in the background.", ok = false)
                }
        }

        private suspend fun runPrompt(app: Application, id: Int, advance: Boolean) {
            val item = ScheduledPrompts.get(app, id) ?: return
            val outcome = try {
                withTimeoutOrNull(RUN_TIMEOUT_MS) { execute(app, item.prompt) }
                    ?: AgentOutcome(false, "It took too long, so Ultron stopped.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled prompt #$id failed", e)
                AgentOutcome(false, "Could not reach Gemini. Check the internet and your Gemini key.")
            }
            if (advance) ScheduledPrompts.finishRun(app, id, outcome.summary, outcome.ok)
            else ScheduledPrompts.recordRun(app, id, outcome.summary, outcome.ok)
            if (outcome.needsUnlock) showUnlockToRun(app, id, item.prompt)
            else AlertNotifications.show(app, if (outcome.ok) "Done: ${item.prompt}" else "Could not do: ${item.prompt}", outcome.summary)
        }

        private suspend fun execute(app: Application, prompt: String): AgentOutcome {
            val license = LicenseStore(app)
            if (!isUsableOffline(license.lastStatus(), license.checkedAt, System.currentTimeMillis() / 1000)) {
                return AgentOutcome(false, "Ultron is not activated on this phone, so the task did not run.")
            }
            val apiKey = ApiKeyStore(app).getApiKey().orEmpty()
            if (apiKey.isBlank()) return AgentOutcome(false, "Add your Gemini key in Settings so scheduled tasks can run.")
            val settings = AppSettingsStore(app).settings.first()
            val locked = app.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            val tools = settings.enabledDeviceTools
                .filterNot { it in NEVER_IN_BACKGROUND || (locked && it in NEEDS_SCREEN) }
                .sortedBy { it.ordinal }
            val runner = DeviceToolRunner(app, app.getSystemService(AudioManager::class.java), { null }, { }, { })
            val agent = ScheduledPromptAgent(apiKey, tools, locked, settings.ownerName, runTool = { tool, args -> runner.run(tool, args) })
            val now = ScheduledPrompts.describeTime(System.currentTimeMillis())
            return agent.run(prompt, now)
        }

        /** Asks the user to unlock and tap, then the task runs (without moving a repeating prompt on again). */
        private fun showUnlockToRun(context: Context, id: Int, prompt: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(
                NotificationChannel(UNLOCK_CHANNEL_ID, "Scheduled tasks waiting", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, UNLOCK_NOTIFICATION_BASE + id,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_RUN_SCHEDULE, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, UNLOCK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Unlock to run: ${prompt.take(50)}")
                .setContentText("The phone was locked. Tap to unlock and Ultron will do it now.")
                .setStyle(Notification.BigTextStyle().bigText("\"$prompt\"\nThe phone was locked. Tap to unlock and Ultron will do it now."))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(UNLOCK_NOTIFICATION_BASE + id, notification)
        }

        /** Clears the "unlock to run" notification once the run has been started from it. */
        fun clearUnlockNotice(context: Context, id: Int) {
            context.getSystemService(NotificationManager::class.java)?.cancel(UNLOCK_NOTIFICATION_BASE + id)
        }

        private const val UNLOCK_CHANNEL_ID = "scheduled_unlock"
    }
}
