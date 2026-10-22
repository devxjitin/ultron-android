package com.assistant.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.assistant.app.data.YouTubeDownloader

/**
 * Keeps the app's process in the foreground while video downloads run, so Android does not stop them when the app
 * goes to the background. The downloads themselves run in [YouTubeDownloader]; this service only shows that they
 * are happening and is stopped as soon as the last one ends.
 */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(YouTubeDownloader.CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, YouTubeDownloader.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading videos")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 3999

        /** Called when a download is queued. Android may refuse to start a foreground service from the background; the download then simply runs without one. */
        fun begin(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }

        fun end(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
