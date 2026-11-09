package com.rawedit.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rawedit.app.MainActivity
import com.rawedit.app.R

/**
 * Keeps the process alive while a render runs so Android does not kill it when the
 * user switches away or starts a screen recording. The render itself still runs in
 * the Activity, because it needs an attached WebView on the UI thread.
 */
class RenderService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = build(this, "Preparing", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "raw_edit_render"
        private const val NOTIF_ID = 4102

        fun start(context: Context) {
            try {
                val intent = Intent(context, RenderService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // the render still works without the service
            }
        }

        fun update(context: Context, percent: Int, text: String) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.notify(NOTIF_ID, build(context, text, percent, false))
            } catch (t: Throwable) {
                // ignore
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RenderService::class.java))
            } catch (t: Throwable) {
                // ignore
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video rendering",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while a video renders"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun build(
            context: Context,
            text: String,
            percent: Int,
            indeterminate: Boolean
        ): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("raw_edit is making your video")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_render)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, percent.coerceIn(0, 100), indeterminate)
                .build()
        }
    }
}
