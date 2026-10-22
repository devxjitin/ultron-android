package com.assistant.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import com.assistant.app.data.AutomationEngine
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps an ongoing notification visible while the assistant process is running. While a voice
 * session is active it switches to the microphone foreground type, which is what lets the
 * session keep listening after the user opens another app.
 */
class AppForegroundService : Service() {

    private var listening = false
    private var muted = false
    private var sessionStartMs = System.currentTimeMillis()

    /** Turns phone events into automation rule runs. Sticky broadcasts replayed on registering are ignored. */
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast) return
            val event = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> "charger_connected"
                Intent.ACTION_POWER_DISCONNECTED -> "charger_disconnected"
                Intent.ACTION_BATTERY_LOW -> "battery_low"
                Intent.ACTION_USER_PRESENT -> "screen_unlocked"
                AudioManager.ACTION_HEADSET_PLUG -> if (intent.getIntExtra("state", 0) == 1) "headphones_plugged" else null
                else -> null
            }
            if (event != null) AutomationEngine.onEvent(context.applicationContext, event)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(this, eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(eventReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SESSION -> { onStopRequested?.invoke(); return START_STICKY }
            ACTION_TOGGLE_MUTE -> { onMuteRequested?.invoke(); return START_STICKY }
        }
        createNotificationChannel()
        applyMode(intent?.action == ACTION_LISTENING)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Re-posts the notification with the foreground type matching [listening]. */
    private fun applyMode(listening: Boolean) {
        if (listening && !this.listening) sessionStartMs = System.currentTimeMillis()
        this.listening = listening
        if (!listening && !AutomationEngine.hasRules(this)) {
            // Nothing needs the service now, so the notification goes away instead of sitting there idle.
            if (instance === this) instance = null
            startForeground(NOTIFICATION_ID, buildNotification(false)) // a started foreground service must show once before it stops
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val notification = buildNotification(listening)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (listening) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ultron status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows what Ultron is doing while it runs in the background."
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        // A channel's importance cannot be lowered after creation, so the old HIGH one is replaced.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(channel)
    }

    internal fun buildNotification(listening: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun serviceIntent(code: Int, action: String) = PendingIntent.getService(
            this, code, Intent(this, AppForegroundService::class.java).setAction(action), flags
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
        if (!listening) {
            return builder
                .setContentTitle(statusTitle)
                .setContentText(statusText)
                .setStyle(Notification.BigTextStyle().bigText(statusText))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(true)
                .setWhen(statusTimeMillis)
                .build()
        }
        // A running session looks like a live call: name, running timer, Mute and a red end button.
        val muteAction = Notification.Action.Builder(null, if (muted) "Unmute" else "Mute", serviceIntent(2, ACTION_TOGGLE_MUTE)).build()
        builder
            .setContentText(statusText)
            .setCategory(Notification.CATEGORY_CALL)
            .setShowWhen(true)
            .setWhen(sessionStartMs)
            .setUsesChronometer(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val person = android.app.Person.Builder().setName("Live with Ultron").setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forOngoingCall(person, serviceIntent(1, ACTION_STOP_SESSION)))
                .addAction(muteAction)
        } else {
            builder.setContentTitle("Live with Ultron")
                .addAction(muteAction)
                .addAction(Notification.Action.Builder(null, "End", serviceIntent(1, ACTION_STOP_SESSION)).build())
        }
        return builder.build()
    }

    companion object {
        private const val LEGACY_CHANNEL_ID = "assistant_status"
        private const val CHANNEL_ID = "assistant_status_low"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_LISTENING = "com.assistant.app.action.LISTENING"
        private const val ACTION_STOP_SESSION = "com.assistant.app.action.STOP_SESSION"
        private const val ACTION_TOGGLE_MUTE = "com.assistant.app.action.TOGGLE_MUTE"

        @Volatile private var instance: AppForegroundService? = null

        @Volatile private var statusTitle = "Ultron"
        @Volatile private var statusText = "Standing by"
        @Volatile private var statusTimeMillis = System.currentTimeMillis()

        /**
         * Shows what the assistant is doing right now, stamped with the time of this update.
         * Reposts quietly (no sound) when the service is already running.
         */
        fun setStatus(context: Context, title: String, text: String) {
            if (title == statusTitle && text == statusText) return
            statusTitle = title
            statusText = text
            statusTimeMillis = System.currentTimeMillis()
            runCatching {
                val running = instance ?: return
                context.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, running.buildNotification(running.listening))
            }
        }

        /** Called on the main thread when the user taps Stop in the notification. */
        @Volatile var onStopRequested: (() -> Unit)? = null
        @Volatile var onMuteRequested: (() -> Unit)? = null

        /** Keeps the Mute button's label in step with the mic. */
        fun setMuted(context: Context, muted: Boolean) {
            val running = instance ?: return
            if (running.muted == muted) return
            running.muted = muted
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, running.buildNotification(running.listening))
            }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AppForegroundService::class.java))
        }

        /**
         * Switches the notification between "ready" and "listening". Going to listening must happen
         * while the app is visible (Android refuses to start microphone services from the
         * background); going back is always allowed. Failures are swallowed so a refused switch
         * never takes the session down with it.
         */
        fun setListening(context: Context, listening: Boolean) {
            runCatching {
                val running = instance
                if (running != null) {
                    running.applyMode(listening)
                } else if (listening) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AppForegroundService::class.java).setAction(ACTION_LISTENING)
                    )
                }
            }
        }
    }
}
