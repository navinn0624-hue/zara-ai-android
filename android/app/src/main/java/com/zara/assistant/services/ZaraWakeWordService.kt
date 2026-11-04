package com.zara.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zara.assistant.MainActivity
import com.zara.assistant.overlay.FloatingOverlayManager

class ZaraWakeWordService : Service() {

    companion object {
        private const val CHANNEL_ID = "ZaraAssistantChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER_OVERLAY = "com.zara.assistant.TRIGGER_OVERLAY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_OVERLAY) {
            FloatingOverlayManager.show(applicationContext)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zara AI Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Zara active in background for instant voice commands"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingTapIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val triggerIntent = Intent(this, ZaraWakeWordService::class.java).apply {
            action = ACTION_TRIGGER_OVERLAY
        }
        val pendingTriggerIntent = PendingIntent.getService(
            this, 1, triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zara AI is Active 🎙️")
            .setContentText("Tap 'Wake Zara' or speak to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingTapIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Wake Zara ✨", pendingTriggerIntent)
            .setOngoing(true)
            .build()
    }
}
