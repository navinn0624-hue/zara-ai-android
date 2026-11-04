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

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 80
        }

        windowManager?.addView(overlayView, layoutParams)

        // Haptic feedback
        vibrate(context)

        // Setup Text To Speech
        initTTS(context)

        // Setup View Listeners
        val btnClose = overlayView?.findViewById<ImageView>(R.id.btnCloseOverlay)
        btnClose?.setOnClickListener {
            dismiss()
        }

        // Start listening to voice command
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
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus?.text = "Listening... Speak in Tamil or English"
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus?.text = "Processing command..."
            }

            override fun onError(error: Int) {
                tvStatus?.text = "Didn't catch that. Tap close and try again."
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
                speak("WhatsApp-la $contact-ku message anupuren")

                val service = ZaraAccessibilityService.instance
                if (service != null) {
                    service.sendWhatsAppMessage(contact, message)
                } else {
                    tvStatus?.text = "Please enable Accessibility Permission for Zara!"
                }

                autoDismiss(2500)
            }

            override fun onAppOpen(appName: String) {
                tvStatus?.text = "Opening $appName..."
                speak("$appName open panren")

                val service = ZaraAccessibilityService.instance
                val opened = service?.launchAppByName(appName) ?: false
                if (!opened) {
                    // Fallback using direct context intent
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
                autoDismiss(3000)
            }
        })
    }

    private fun initTTS(context: Context) {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale("ta", "IN")
                }
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZARA_TTS")
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(100)
        }
    }

    private fun autoDismiss(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, delayMs)
    }
}
