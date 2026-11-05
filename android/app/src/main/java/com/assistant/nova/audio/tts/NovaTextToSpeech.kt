package com.assistant.nova.audio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.assistant.nova.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class NovaTextToSpeech(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val tamilLocale = Locale("ta", "IN")
    private val englishLocale = Locale.US

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            applyVoiceSettings()
            Log.d(TAG, "TextToSpeech successfully initialized")
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech: status $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.w(TAG, "TTS playback error for utterance $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                Log.w(TAG, "TTS playback error code $errorCode for utterance $utteranceId")
            }
        })
    }

    fun applyVoiceSettings() {
        tts?.let { engine ->
            engine.setSpeechRate(preferencesManager.ttsSpeechRate)
            engine.setPitch(preferencesManager.ttsPitch)
        }
    }

    fun speak(text: String, onCompletion: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank() || !preferencesManager.autoSpeakResponses) {
            onCompletion?.invoke()
            return
        }

        applyVoiceSettings()

        // Detect script: if text has Tamil characters, switch TTS engine locale to Tamil
        val hasTamilChars = text.any { it in '\u0B80'..'\u0BFF' }
        val targetLocale = if (hasTamilChars) tamilLocale else englishLocale

        val langResult = tts?.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Target language $targetLocale not fully supported by TTS, falling back to English")
            tts?.setLanguage(englishLocale)
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "NovaTextToSpeech"
    }
}
