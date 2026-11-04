package com.zara.assistant.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
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

    fun show(context: Context) {
        if (overlayView != null) return // Already showing

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
            gravity = Gravity.BOTTOM
            y = 70
        }

        windowManager?.addView(overlayView, layoutParams)

        // Haptic feedback
        vibrate(context)

        // Initialize Natural Female Voice (TTS)
        initTTS(context)

        // Setup Close Button
        overlayView?.findViewById<ImageView>(R.id.btnCloseOverlay)?.setOnClickListener {
            dismiss()
        }

        // Start listening to voice command in Tamil/Tanglish/English
        startSpeechListening(context)
    }

    fun dismiss() {
        try {
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

    private fun startSpeechListening(context: Context) {
        val tvSpeech = overlayView?.findViewById<TextView>(R.id.tvLiveSpeech)
        val tvStatus = overlayView?.findViewById<TextView>(R.id.tvAssistantStatus)

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
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus?.text = "Processing command..."
            }

            override fun onError(error: Int) {
                tvStatus?.text = "Didn't catch that. Please speak again."
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

        VoiceBrainEngine.process(command, object : VoiceBrainEngine.CommandCallback {
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
                autoDismiss(3200)
            }
        })
    }

    /**
     * Natural sweet female voice for Zara
     */
    private fun initTTS(context: Context) {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.apply {
                        language = Locale("ta", "IN")
                        setPitch(1.15f) // Feminine energetic pitch
                        setSpeechRate(1.02f)

                        // Choose high quality female voice if available on device
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
