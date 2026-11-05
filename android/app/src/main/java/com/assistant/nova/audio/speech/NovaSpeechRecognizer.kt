package com.assistant.nova.audio.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.assistant.nova.domain.model.LanguagePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface SpeechState {
    data object Idle : SpeechState
    data object Listening : SpeechState
    data object Processing : SpeechState
    data class Error(val message: String) : SpeechState
}

class NovaSpeechRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _rmsAmplitude = MutableStateFlow(0f)
    val rmsAmplitude: StateFlow<Float> = _rmsAmplitude.asStateFlow()

    var onSpeechResult: ((String) -> Unit)? = null
    var onPartialSpeechResult: ((String) -> Unit)? = null

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        } else {
            Log.e(TAG, "SpeechRecognizer is not available on this device")
            _state.value = SpeechState.Error("Speech recognition not available")
        }
    }

    fun startListening(languagePreference: LanguagePreference = LanguagePreference.AUTO) {
        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            when (languagePreference) {
                LanguagePreference.TAMIL -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                }
                LanguagePreference.ENGLISH -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                }
                LanguagePreference.AUTO -> {
                    // Default to English, with Tamil as extra supported language
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_ADDITIONAL_LANGUAGES, arrayOf("ta-IN", "en-IN", "en-US"))
                }
            }
        }

        try {
            _state.value = SpeechState.Listening
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _state.value = SpeechState.Error(e.localizedMessage ?: "Failed to start listening")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _state.value = SpeechState.Processing
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            _state.value = SpeechState.Idle
            _rmsAmplitude.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            _state.value = SpeechState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognition", e)
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {
                _state.value = SpeechState.Listening
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB typically ranges from -2dB to +10dB
                // Normalize to 0.0 .. 1.0 range for the waveform visualizer
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsAmplitude.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = SpeechState.Processing
                _rmsAmplitude.value = 0f
            }

            override fun onError(error: Int) {
                _rmsAmplitude.value = 0f
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
                    else -> "Speech recognition error ($error)"
                }
                Log.w(TAG, "SpeechRecognizer error: $errorMessage ($error)")
                _state.value = SpeechState.Error(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                _rmsAmplitude.value = 0f
                _state.value = SpeechState.Idle
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d(TAG, "Recognized final speech: $recognizedText")
                    onSpeechResult?.invoke(recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partial = matches[0]
                    onPartialSpeechResult?.invoke(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    companion object {
        private const val TAG = "NovaSpeechRecognizer"
    }
}
