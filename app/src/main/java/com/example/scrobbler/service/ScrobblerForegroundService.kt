package com.example.scrobbler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scrobbler.R
import com.example.scrobbler.SetupActivity
import com.example.scrobbler.util.Logger

class ScrobblerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID      = "scrobbler_fg"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "START"
        const val ACTION_STOP     = "STOP"
        const val EXTRA_TRACK_LINE = "track_line"

        fun startWith(context: Context, trackLine: String = "") {
            val intent = Intent(context, ScrobblerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRACK_LINE, trackLine)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScrobblerForegroundService::class.java)
                    .apply { action = ACTION_STOP }
            )
        }
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val trackLine = intent.getStringExtra(EXTRA_TRACK_LINE) ?: ""
                startForeground(NOTIFICATION_ID, buildNotification(trackLine))
                Logger.d("Foreground service started")
            }
            ACTION_STOP -> {
                Logger.d("Foreground service stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(trackLine: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (trackLine.isBlank()) "Monitoring Musicolet…" else trackLine
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scrobbler)
            .setContentTitle("Scrobbling")
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scrobbler Status",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Shows currently scrobbling track"
            setShowBadge(false)
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
}