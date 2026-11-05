package com.zara.assistant.overlay

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.zara.assistant.R
import com.zara.assistant.engine.VoiceBrainEngine
import com.zara.assistant.services.ZaraAccessibilityService
import java.util.Locale

object FloatingOverlayManager {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var rotateAnimator: ObjectAnimator? = null

    fun show(context: Context) {
        if (overlayView != null) return // Already active

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.layout_floating_orb, null)

        @Suppress("DEPRECATION")
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 35
        }

        windowManager?.addView(overlayView, layoutParams)

        // Haptic tap
        vibrate(context)

        // Start iPhone Siri Glowing Orb Animations
        startSiriAnimations()

        // Init Natural Female TTS
        initTTS(context)

        // Setup Close Button & Tap-to-Speak on Orb
        overlayView?.findViewById<ImageView>(R.id.btnCloseOverlay)?.setOnClickListener {
            dismiss()
        }

        overlayView?.findViewById<View>(R.id.frameSiriOrb)?.setOnClickListener {
            startSpeechListening(context)
        }

        // Start speech listening in Tamil/Tanglish/English
        startSpeechListening(context)
    }

    fun dismiss() {
        try {
            pulseAnimator?.cancel()
            rotateAnimator?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Exact iPhone Siri Breathing Scale & Rotating Gradient Animations
     */
    private fun startSiriAnimations() {
        val orbCore = overlayView?.findViewById<View>(R.id.viewGlowCore) ?: return
        val orbMiddle = overlayView?.findViewById<View>(R.id.viewGlowMiddle) ?: return
        val orbOuter = overlayView?.findViewById<View>(R.id.viewGlowOuter) ?: return

        // Siri Breathing Pulse (Scale 0.93f -> 1.14f)
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.93f, 1.14f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.93f, 1.14f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(orbMiddle, scaleX, scaleY).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Siri Ambient Halo Slow Rotation
        rotateAnimator = ObjectAnimator.ofFloat(orbOuter, View.ROTATION, 0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun startSpeechListening(context: Context) {
        val tvSpeech = overlayView?.findViewById<TextView>(R.id.tvLiveSpeech)
        val tvStatus = overlayView?.findViewById<TextView>(R.id.tvAssistantStatus)

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "ta"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus?.text = "Listening... Speak in Tamil, English, or Tanglish ✨"
            }

            override fun onBeginningOfSpeech() {}

            /**
             * Real-time Audio Visualizer: Siri Orb expands and glows with user voice volume!
             */
            override fun onRmsChanged(rmsdB: Float) {
                val orbMiddle = overlayView?.findViewById<View>(R.id.viewGlowMiddle)
                val orbCore = overlayView?.findViewById<View>(R.id.viewGlowCore)
                val boost = 1.0f + (rmsdB / 14f).coerceIn(0f, 0.35f)
                orbMiddle?.scaleX = boost
                orbMiddle?.scaleY = boost
                orbCore?.scaleX = boost * 0.95f
                orbCore?.scaleY = boost * 0.95f
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus?.text = "Thinking..."
            }

            override fun onError(error: Int) {
                tvStatus?.text = "Didn't catch that. Tap the orb to speak."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    tvSpeech?.text = "\"$spoken\""
                    handleCommand(context, spoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    tvSpeech?.text = "\"${matches[0]}\""
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(context: Context, command: String) {
        val tvStatus = overlayView?.findViewById<TextView>(R.id.tvAssistantStatus)

        VoiceBrainEngine.process(context, command, object : VoiceBrainEngine.CommandCallback {
            override fun onWhatsAppSend(contact: String, message: String) {
                tvStatus?.text = "Opening WhatsApp & sending to $contact..."
                speak("WhatsApp-la $contact-ku message anupuren...")

                val service = ZaraAccessibilityService.instance
                if (service != null) {
                    service.sendWhatsAppMessage(contact, message)
                } else {
                    tvStatus?.text = "Please turn ON Accessibility for Zara in Settings!"
                }
                autoDismiss(2800)
            }

            override fun onSetTimer(seconds: Int, label: String) {
                val mins = seconds / 60
                val secs = seconds % 60
                val timeStr = if (mins > 0) "$mins minutes" else "$secs seconds"
                tvStatus?.text = "Setting timer for $timeStr ⏱️"
                speak("$timeStr timer set panren")

                try {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    tvStatus?.text = "Timer set failed: ${e.message}"
                }
                autoDismiss(2200)
            }

            override fun onSetAlarm(hour: Int, minute: Int, label: String) {
                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                tvStatus?.text = "Setting alarm for $timeStr ⏰"
                speak("$timeStr-ku alarm set panren")

                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    tvStatus?.text = "Alarm set failed: ${e.message}"
                }
                autoDismiss(2200)
            }

            override fun onToggleTorch(enable: Boolean) {
                val text = if (enable) "Turning Torch ON 🔦" else "Turning Torch OFF 🔦"
                tvStatus?.text = text
                speak(if (enable) "Torch on panniten" else "Torch off panniten")

                try {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cm.cameraIdList[0]
                    cm.setTorchMode(cameraId, enable)
                } catch (e: Exception) {
                    tvStatus?.text = "Torch control error"
                }
                autoDismiss(1800)
            }

            override fun onPhoneCall(contactOrNumber: String) {
                tvStatus?.text = "Calling $contactOrNumber 📞"
                speak("$contactOrNumber-ku call panren")

                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactOrNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    tvStatus?.text = "Cannot make call"
                }
                autoDismiss(1800)
            }

            override fun onYouTubePlay(query: String) {
                tvStatus?.text = "Playing $query on YouTube ▶️"
                speak("YouTube-la $query play panren")

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=${Uri.encode(query)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                }
                autoDismiss(2000)
            }

            override fun onAppOpen(appName: String) {
                tvStatus?.text = "Opening $appName..."
                speak("$appName open panren")

                val service = ZaraAccessibilityService.instance
                val opened = service?.launchAppByName(appName) ?: false
                if (!opened) {
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage("com.$appName.android")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
                autoDismiss(1800)
            }

            override fun onConversational(reply: String) {
                tvStatus?.text = reply
                speak(reply)
                autoDismiss(5000)
            }
        })
    }

    private fun initTTS(context: Context) {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.apply {
                        language = Locale("ta", "IN")
                        setPitch(1.15f)
                        setSpeechRate(1.02f)

                        val voices = voices ?: emptySet()
                        for (voice in voices) {
                            val name = voice.name.lowercase()
                            if ((name.contains("female") || name.contains("#female") || name.contains("f00")) &&
                                (voice.locale.language == "ta" || voice.locale.language == "en")
                            ) {
                                this.voice = voice
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZARA_FEMALE_TTS")
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(120)
        }
    }

    private fun autoDismiss(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, delayMs)
    }
}
