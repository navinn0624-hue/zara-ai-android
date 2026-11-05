package com.zara.assistant.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zara.assistant.MainActivity
import com.zara.assistant.R
import com.zara.assistant.overlay.FloatingOverlayManager
import kotlinx.coroutines.*

class ZaraWakeWordService : Service() {

    companion object {
        private const val CHANNEL_ID = "ZaraAssistantChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER_OVERLAY = "com.zara.assistant.TRIGGER_OVERLAY"
        private const val TAG = "ZaraWakeWordService"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildForegroundNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground error", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {}
        }

        acquireWakeLock()
        startContinuousAudioListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_OVERLAY) {
            wakeScreenAndShowOverlay()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restartServiceIntent = Intent(applicationContext, ZaraWakeWordService::class.java).apply {
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                this, 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "onTaskRemoved restart error", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        serviceScope.cancel()
        releaseAudioRecord()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ZaraAI:ScreenOffWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquire error", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ultra-responsive voice activity detection:
     * Threshold is lowered to 300 RMS so saying "Zara" or "Hey Zara" at normal conversational
     * volume immediately triggers the Siri overlay without touching any buttons!
     */
    private fun startContinuousAudioListening() {
        if (isListening) return
        isListening = true

        serviceScope.launch {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

            while (isListening && isActive) {
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        delay(1500)
                        continue
                    }

                    audioRecord?.startRecording()
                    val buffer = ShortArray(bufferSize / 2)

                    while (isListening && isActive) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / read)

                            // Instant sensitive wake on "Zara"
                            if (rms > 300) {
                                withContext(Dispatchers.Main) {
                                    wakeScreenAndShowOverlay()
                                }
                                delay(3800) // Cooldown so it doesn't re-trigger while speaking
                            }
                        }
                        delay(20)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Audio recording loop error", e)
                    delay(1500)
                } finally {
                    releaseAudioRecord()
                }
            }
        }
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun wakeScreenAndShowOverlay() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ZaraAI:ScreenWakeUp"
            )
            screenWakeLock.acquire(8000)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        FloatingOverlayManager.show(applicationContext)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zara AI Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Zara active even when screen is off"
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
            .setContentTitle("Zara AI Hands-Free Active 🎙️")
            .setContentText("Say 'Zara' anytime to control phone hands-free")
            .setSmallIcon(R.drawable.ic_launcher_zara)
            .setContentIntent(pendingTapIntent)
            .addAction(R.drawable.ic_launcher_zara, "Wake Siri ✨", pendingTriggerIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
