package com.assistant.nova.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.assistant.nova.MainActivity
import com.assistant.nova.NovaApplication
import java.util.Locale

class NovaWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListeningActive = false
    private var isReArming = false

    override fun onCreate() {
        super.onCreate()
        acquireCpuWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        initContinuousRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startListeningLoop()
        return START_STICKY
    }

    private fun acquireCpuWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NovaAssistant::BackgroundWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety lease
            }
            Log.d(TAG, "CPU WakeLock acquired for background hotword listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA Background Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps NOVA voice detection active when screen is off or using other apps."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, NovaWakeWordService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVA Personal Voice Assistant")
            .setContentText("Always listening for 'Hey NOVA' • Hands-free control active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initContinuousRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition unavailable on this device")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListeningActive = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListeningActive = false
                    }

                    override fun onError(error: Int) {
                        isListeningActive = false
                        Log.d(TAG, "Hotword recognizer error: $error. Re-arming listener...")
                        scheduleReArm(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 400L)
                    }

                    override fun onResults(results: Bundle?) {
                        isListeningActive = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            checkHotwordMatch(matches)
                        } else {
                            scheduleReArm(300L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            checkHotwordMatch(matches)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing hotword speech recognizer", e)
        }
    }

    private fun checkHotwordMatch(matches: List<String>) {
        for (utterance in matches) {
            val lower = utterance.lowercase().trim()
            if (lower.contains("nova") || lower.contains("hey nova") || lower.contains("நோவா")) {
                Log.i(TAG, "HOTWORD DETECTED in background: '$utterance'!")
                onWakeWordTriggered()
                return
            }
        }
        // If not matched, re-arm
        scheduleReArm(250L)
    }

    private fun onWakeWordTriggered() {
        // 1. Haptic vibration
        vibrateFeedback()

        // 2. Wake up the phone screen if off
        wakeScreenUp()

        // 3. Launch MainActivity with auto-listen flag to immediately take voice command
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra(MainActivity.EXTRA_AUTO_START_LISTENING, true)
        }
        startActivity(launchIntent)

        // Temporarily pause listening while user is interacting with main UI
        isListeningActive = false
        scheduleReArm(6000L)
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun wakeScreenUp() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "NovaAssistant::ScreenWakeLock"
            )
            screenWakeLock.acquire(4000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen", e)
        }
    }

    private fun startListeningLoop() {
        val app = applicationContext as? NovaApplication
        if (app?.preferencesManager?.isBackgroundListeningEnabled != true) {
            Log.d(TAG, "Background listening disabled in preferences")
            return
        }

        if (isListeningActive) return

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initContinuousRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-US"))
                }

                speechRecognizer?.startListening(intent)
                isListeningActive = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background listening loop", e)
                scheduleReArm(1500L)
            }
        }
    }

    private fun scheduleReArm(delayMillis: Long) {
        if (isReArming) return
        isReArming = true
        mainHandler.postDelayed({
            isReArming = false
            startListeningLoop()
        }, delayMillis)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up service", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "NovaWakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nova_wakeword_channel"
        const val ACTION_STOP_SERVICE = "com.assistant.nova.ACTION_STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, NovaWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NovaWakeWordService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
