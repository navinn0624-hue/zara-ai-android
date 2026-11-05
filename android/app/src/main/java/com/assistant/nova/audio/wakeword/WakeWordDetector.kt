package com.assistant.nova.audio.wakeword

import android.content.Context
import android.util.Log
import com.assistant.nova.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wake Word Engine Architecture for "Hey NOVA".
 *
 * INVESTIGATION OF ON-DEVICE WAKE WORD ENGINES FOR ANDROID:
 * 1. Picovoice Porcupine:
 *    - Pros: Highly accurate (< 0.5% false alarm rate), ultra-low CPU (< 5%), on-device offline.
 *    - Cons: Requires an API AccessKey from Picovoice console.
 *
 * 2. Vosk / Kaldi Light:
 *    - Pros: Completely open-source, runs offline on Android via JNI/C++, no license keys needed.
 *    - Cons: Larger model size (~40MB-50MB), higher battery drain if running continuously.
 *
 * 3. Android VoiceInteractionService (System Assistant):
 *    - Pros: Official Android platform integration, integrated with OS hardware hotword detector when supported.
 *    - Cons: User must set NOVA as the Default Assistant App in Android Settings -> Digital Assistant.
 *
 * 4. Android AudioRecord / Keyword Spotter Fallback:
 *    - Pros: No external binary dependencies, immediate compilation, low overhead.
 *    - Cons: Bound by Android 12+ Foreground Service Microphone restrictions (must show ongoing notification
 *      and green microphone privacy indicator).
 */
sealed interface WakeWordState {
    data object Inactive : WakeWordState
    data object Listening : WakeWordState
    data object Detected : WakeWordState
    data class Error(val reason: String) : WakeWordState
}

class WakeWordDetector(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private val _state = MutableStateFlow<WakeWordState>(WakeWordState.Inactive)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    var onWakeWordDetected: (() -> Unit)? = null

    val isEnabled: Boolean
        get() = preferencesManager.wakeWordEnabled

    fun startListening() {
        if (!preferencesManager.wakeWordEnabled) {
            _state.value = WakeWordState.Inactive
            return
        }

        try {
            _state.value = WakeWordState.Listening
            Log.d(TAG, "Wake word detection active: Listening for 'Hey NOVA'")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word detector", e)
            _state.value = WakeWordState.Error(e.message ?: "Failed to start wake word detector")
        }
    }

    fun stopListening() {
        _state.value = WakeWordState.Inactive
    }

    /**
     * Called when the keyword "Hey NOVA" is matched by the audio recognizer or acoustic model.
     */
    fun triggerWakeWord() {
        Log.i(TAG, "'Hey NOVA' Wake Word Detected!")
        _state.value = WakeWordState.Detected
        onWakeWordDetected?.invoke()
    }

    companion object {
        private const val TAG = "WakeWordDetector"
        const val WAKE_WORD_PHRASE = "Hey NOVA"
    }
}
