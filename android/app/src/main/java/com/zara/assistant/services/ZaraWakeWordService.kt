package com.zara.assistant.services

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
            Log.e(TAG, "Failed to start foreground with microphone type", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground failed", ex)
            }
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
     * Highly sensitive voice detection loop using VOICE_RECOGNITION audio source
     * Detects voice calls even when screen is locked or app is in background!
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
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord init failed, retrying with MIC...")
                        audioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            bufferSize
                        )
                    }

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        delay(2500)
                        continue
                    }

                    audioRecord?.startRecording()
                    val buffer = ShortArray(bufferSize / 2)
                    var speechFrames = 0

                    while (isListening && isActive) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / read)

                            // Lowered threshold (1100) for effortless activation from 1-2 meters away
                            if (rms > 1100) {
                                speechFrames++
                                if (speechFrames >= 2) {
                                    speechFrames = 0
                                    withContext(Dispatchers.Main) {
                                        wakeScreenAndShowOverlay()
                                    }
                                    delay(4000) // Cooldown after activation
                                }
                            } else {
                                if (speechFrames > 0) speechFrames--
                            }
                        }
                        delay(25)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Continuous audio listening loop error", e)
                    delay(2000)
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

    /**
     * Wakes up the screen if it was off, and shows Zara's Siri overlay!
     */
    private fun wakeScreenAndShowOverlay() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ZaraAI:WakeScreen"
            )
            screenWakeLock.acquire(4000)
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
            .setContentTitle("Zara AI is Listening 🎙️")
            .setContentText("Say 'Zara' or tap here to wake Siri overlay")
            .setSmallIcon(R.drawable.ic_launcher_zara)
            .setContentIntent(pendingTapIntent)
            .addAction(R.drawable.ic_launcher_zara, "Wake Siri ✨", pendingTriggerIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
