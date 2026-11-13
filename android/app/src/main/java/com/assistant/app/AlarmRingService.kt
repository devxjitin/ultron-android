package com.assistant.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import com.assistant.app.data.AlertNotifications
import com.assistant.app.data.OwnClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rings one of Ultron's own alarms or timers. The sound is played by this service on the alarm volume, so it does not
 * depend on the phone's clock app or on how notification sounds are set up, and it keeps ringing until it is stopped,
 * snoozed, or [MAX_RING_MS] has passed. The notification carries Stop and Snooze and opens [AlarmActivity] over the
 * lock screen.
 */
class AlarmRingService : Service() {

    /** What is ringing right now. */
    data class Ring(val kind: String, val label: String, val startedAt: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { missed() }
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> {
                state.value?.let { OwnClock.snooze(applicationContext, it.label, SNOOZE_MINUTES) }
                stopRinging()
            }
            else -> ring(
                intent?.getStringExtra(EXTRA_KIND) ?: OwnClock.KIND_ALARM,
                intent?.getStringExtra(EXTRA_LABEL).orEmpty()
            )
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        silence()
        if (state.value != null) state.value = null
        super.onDestroy()
    }

    private fun ring(kind: String, label: String) {
        val ring = Ring(kind, label, System.currentTimeMillis())
        try {
            val notification = buildNotification(ring)
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Android refused the foreground start; an ordinary alarm notification is better than silence.
            Log.e("AssistantError", "Alarm service could not start in the foreground", e)
            AlertNotifications.post(this, title(ring), text(ring), urgent = true, kind = kind)
            stopSelf()
            return
        }
        state.value = ring
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, MAX_RING_MS)
        // A second alarm while one rings only changes what the screen says; the sound is already playing.
        if (player != null) return
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ultron:alarm").apply { acquire(MAX_RING_MS + 10_000L) }
        }
        startSound()
        startVibration()
    }

    private fun startSound() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
        runCatching { getSystemService(AudioManager::class.java).requestAudioFocus(request) }
        focusRequest = request
        // Some phones have no alarm tone chosen, so the ringtone and then the notification tone are tried.
        val tones = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .mapNotNull { type ->
                runCatching { RingtoneManager.getActualDefaultRingtoneUri(this, type) }.getOrNull() ?: RingtoneManager.getDefaultUri(type)
            }
        for (tone in tones) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(this@AlarmRingService, tone)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { Log.w("AssistantError", "Alarm tone $tone could not be played", it) }.getOrNull()
            if (started != null) {
                player = started
                return
            }
        }
    }

    private fun startVibration() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun silence() {
        handler.removeCallbacks(giveUp)
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
        focusRequest?.let { runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun stopRinging() {
        silence()
        state.value = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    /** Nobody stopped it in time: go quiet and leave a note saying it rang. */
    private fun missed() {
        val ring = state.value
        stopRinging()
        if (ring != null) {
            AlertNotifications.post(
                this, if (ring.kind == OwnClock.KIND_TIMER) "Timer finished" else "Missed alarm", text(ring), urgent = false, kind = null
            )
        }
    }

    private fun title(ring: Ring) = if (ring.kind == OwnClock.KIND_TIMER) "Timer" else "Alarm"

    private fun text(ring: Ring) = ring.label.ifBlank { if (ring.kind == OwnClock.KIND_TIMER) "Time is up" else OwnClock.clockTime(ring.startedAt) }

    private fun buildNotification(ring: Ring): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ringing alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm or timer is ringing, with Stop and Snooze."
                // The service plays the tone itself on the alarm volume, so the channel stays silent.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION), flags
        )
        fun action(code: Int, name: String) = PendingIntent.getService(this, code, Intent(this, AlarmRingService::class.java).setAction(name), flags)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title(ring))
            .setContentText(text(ring))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(Notification.Action.Builder(null, "Stop", action(1, ACTION_STOP)).build())
        if (ring.kind == OwnClock.KIND_ALARM) {
            builder.addAction(Notification.Action.Builder(null, "Snooze $SNOOZE_MINUTES min", action(2, ACTION_SNOOZE)).build())
        }
        return builder.build()
    }

    companion object {
        const val SNOOZE_MINUTES = 10
        /** An alarm nobody answers stops by itself after this long. */
        const val MAX_RING_MS = 5 * 60_000L

        private const val CHANNEL_ID = "assistant_ringing"
        private const val NOTIFICATION_ID = 1900
        private const val ACTION_STOP = "com.assistant.app.alarm.STOP"
        private const val ACTION_SNOOZE = "com.assistant.app.alarm.SNOOZE"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_LABEL = "label"

        private val state = MutableStateFlow<Ring?>(null)

        /** The alarm or timer ringing now, or null. */
        val current: StateFlow<Ring?> get() = state

        /** Starts ringing. False when Android refused to start the service, so the caller can fall back to a notification. */
        fun start(context: Context, kind: String, label: String): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, AlarmRingService::class.java).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_LABEL, label)
            )
        }.onFailure { Log.e("AssistantError", "Alarm service could not be started", it) }.isSuccess

        fun stop(context: Context) = send(context, ACTION_STOP)

        fun snooze(context: Context) = send(context, ACTION_SNOOZE)

        private fun send(context: Context, action: String) {
            if (state.value == null) return
            runCatching { context.startService(Intent(context, AlarmRingService::class.java).setAction(action)) }
        }
    }
}
