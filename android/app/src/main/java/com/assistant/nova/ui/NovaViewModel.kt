package com.assistant.nova.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.nova.NovaApplication
import com.assistant.nova.audio.speech.NovaSpeechRecognizer
import com.assistant.nova.audio.speech.SpeechState
import com.assistant.nova.audio.tts.NovaTextToSpeech
import com.assistant.nova.audio.wakeword.WakeWordDetector
import com.assistant.nova.domain.model.ActionResult
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.domain.model.ChatMessage
import com.assistant.nova.domain.model.LanguagePreference
import com.assistant.nova.domain.model.MessageSender
import com.assistant.nova.domain.model.ThemeMode
import com.assistant.nova.services.NovaWakeWordService
import com.assistant.nova.ui.components.OrbState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovaUiState(
    val orbState: OrbState = OrbState.IDLE,
    val rmsAmplitude: Float = 0f,
    val liveTranscript: String = "",
    val conversations: List<ChatMessage> = emptyList(),
    val pendingConfirmationAction: AgentAction? = null,
    val pendingConfirmationPrompt: String? = null,
    val isSpeaking: Boolean = false,
    val hasMicPermission: Boolean = false,
    val statusMessage: String = "Tap orb or say 'Hey NOVA'",
    val isApiKeySet: Boolean = false,
    val isAwaitingVoiceConfirmation: Boolean = false
)

class NovaViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovaApplication
    private val speechRecognizer = NovaSpeechRecognizer(app)
    private val textToSpeech = NovaTextToSpeech(app, app.preferencesManager)
    private val wakeWordDetector = WakeWordDetector(app, app.preferencesManager)

    private val _themeMode = MutableStateFlow(app.preferencesManager.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isHandsFreeEnabled = MutableStateFlow(app.preferencesManager.isHandsFreeModeEnabled)
    val isHandsFreeEnabled: StateFlow<Boolean> = _isHandsFreeEnabled.asStateFlow()

    private val _isBackgroundListening = MutableStateFlow(app.preferencesManager.isBackgroundListeningEnabled)
    val isBackgroundListening: StateFlow<Boolean> = _isBackgroundListening.asStateFlow()

    private val _uiState = MutableStateFlow(
        NovaUiState(isApiKeySet = app.preferencesManager.isApiKeyConfigured)
    )
    val uiState: StateFlow<NovaUiState> = _uiState.asStateFlow()

    init {
        observeConversations()
        observeAudioStates()
        setupSpeechCallbacks()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            app.repository.getAllConversations().collect { entities ->
                val messages = entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        sender = if (entity.sender == "USER") MessageSender.USER else MessageSender.ASSISTANT,
                        content = entity.message,
                        detectedLanguage = entity.language,
                        timestamp = entity.timestamp
                    )
                }
                _uiState.update { it.copy(conversations = messages) }
            }
        }
    }

    private fun observeAudioStates() {
        // RMS amplitude for Orb & Waveform visualization
        viewModelScope.launch {
            speechRecognizer.rmsAmplitude.collect { rms ->
                _uiState.update { it.copy(rmsAmplitude = rms) }
            }
        }

        // SpeechRecognizer state
        viewModelScope.launch {
            speechRecognizer.state.collect { state ->
                when (state) {
                    is SpeechState.Listening -> {
                        _uiState.update {
                            it.copy(
                                orbState = OrbState.LISTENING,
                                statusMessage = if (it.isAwaitingVoiceConfirmation)
                                    "Say 'Yes' to confirm or 'No' to cancel"
                                else
                                    "Listening… (Tamil & English)"
                            )
                        }
                    }
                    is SpeechState.Processing -> {
                        _uiState.update {
                            it.copy(
                                orbState = OrbState.THINKING,
                                statusMessage = "Processing command…"
                            )
                        }
                    }
                    is SpeechState.Idle -> {
                        if (!_uiState.value.isSpeaking && _uiState.value.orbState != OrbState.THINKING) {
                            _uiState.update {
                                it.copy(
                                    orbState = OrbState.IDLE,
                                    statusMessage = "Tap orb or say 'Hey NOVA'"
                                )
                            }
                        }
                    }
                    is SpeechState.Error -> {
                        _uiState.update {
                            it.copy(
                                orbState = OrbState.IDLE,
                                statusMessage = state.message
                            )
                        }
                    }
                }
            }
        }

        // TTS Speaking state
        viewModelScope.launch {
            textToSpeech.isSpeaking.collect { isSpeaking ->
                _uiState.update {
                    it.copy(
                        isSpeaking = isSpeaking,
                        orbState = if (isSpeaking) OrbState.SPEAKING else OrbState.IDLE
                    )
                }
            }
        }

        // On-device Wake Word callback
        wakeWordDetector.onWakeWordDetected = {
            onAutoVoiceTrigger()
        }
    }

    private fun setupSpeechCallbacks() {
        speechRecognizer.onPartialSpeechResult = { partial ->
            _uiState.update { it.copy(liveTranscript = partial) }
        }

        speechRecognizer.onSpeechResult = { finalUtterance ->
            _uiState.update { it.copy(liveTranscript = finalUtterance) }
            if (_uiState.value.isAwaitingVoiceConfirmation) {
                handleVoiceConfirmationInput(finalUtterance)
            } else {
                submitUserSpeech(finalUtterance)
            }
        }
    }

    fun onMicTrigger() {
        if (_uiState.value.isSpeaking) {
            textToSpeech.stop()
            return
        }

        if (_uiState.value.orbState == OrbState.LISTENING) {
            speechRecognizer.stopListening()
            return
        }

        val pref = app.preferencesManager.languagePreference
        speechRecognizer.startListening(pref)
    }

    /**
     * Triggered automatically when "Hey NOVA" is spoken from background / screen off
     */
    fun onAutoVoiceTrigger() {
        viewModelScope.launch {
            if (_uiState.value.isSpeaking) {
                textToSpeech.stop()
            }
            delay(150)
            _uiState.update {
                it.copy(statusMessage = "Hey NOVA detected! Listening...")
            }
            val pref = app.preferencesManager.languagePreference
            speechRecognizer.startListening(pref)
        }
    }

    fun submitUserSpeech(speechText: String) {
        if (speechText.isBlank()) return

        _uiState.update {
            it.copy(
                orbState = OrbState.THINKING,
                statusMessage = "NOVA is thinking…"
            )
        }

        viewModelScope.launch {
            val result = app.processVoiceInputUseCase.processInput(speechText)

            _uiState.update {
                it.copy(
                    liveTranscript = "",
                    statusMessage = if (result.isSuccess) "Done" else (result.errorMessage ?: "Error")
                )
            }

            // Speak response
            textToSpeech.speak(result.replyText) {
                // Once speech finishes, if an action requires voice confirmation, auto-listen!
                if (_uiState.value.isAwaitingVoiceConfirmation && _isHandsFreeEnabled.value) {
                    viewModelScope.launch {
                        delay(250)
                        speechRecognizer.startListening(LanguagePreference.AUTO)
                    }
                }
            }

            // If an action was identified, execute it or prompt confirmation
            result.action?.let { action ->
                handleAgentAction(action)
            }
        }
    }

    private suspend fun handleAgentAction(action: AgentAction) {
        val actionResult = app.executeAgentActionUseCase.execute(action, userApproved = false)

        when (actionResult) {
            is ActionResult.RequiresConfirmation -> {
                _uiState.update {
                    it.copy(
                        pendingConfirmationAction = actionResult.action,
                        pendingConfirmationPrompt = actionResult.prompt,
                        isAwaitingVoiceConfirmation = true
                    )
                }

                // If hands-free is enabled, voice prompt the user for confirmation
                if (_isHandsFreeEnabled.value) {
                    val promptVoice = "${actionResult.prompt}. Say yes to proceed or no to cancel."
                    textToSpeech.speak(promptVoice) {
                        viewModelScope.launch {
                            delay(300)
                            speechRecognizer.startListening(LanguagePreference.AUTO)
                        }
                    }
                }
            }
            is ActionResult.Success -> {
                _uiState.update { it.copy(statusMessage = actionResult.message) }
            }
            is ActionResult.Failure -> {
                _uiState.update { it.copy(statusMessage = actionResult.error) }
            }
        }
    }

    /**
     * Hands-Free No Touch Voice Confirmation processor
     */
    private fun handleVoiceConfirmationInput(speech: String) {
        val lower = speech.lowercase().trim()

        val positiveKeywords = listOf("yes", "confirm", "proceed", "call", "send", "ok", "okay", "sure", "ஆமா", "சரி", "பண்ணு", "செய்", "call pannu", "send pannu")
        val negativeKeywords = listOf("no", "cancel", "stop", "don't", "dont", "வேண்டாம்", "வேண்டா", "நிறுத்து", "cancel pannu")

        val isPositive = positiveKeywords.any { lower.contains(it) }
        val isNegative = negativeKeywords.any { lower.contains(it) }

        if (isPositive) {
            _uiState.update { it.copy(isAwaitingVoiceConfirmation = false) }
            confirmPendingAction()
        } else if (isNegative) {
            _uiState.update { it.copy(isAwaitingVoiceConfirmation = false) }
            dismissPendingAction()
        } else {
            // Unclear response: ask once more hands-free
            textToSpeech.speak("I didn't catch that. Say yes to confirm or no to cancel.") {
                viewModelScope.launch {
                    delay(300)
                    speechRecognizer.startListening(LanguagePreference.AUTO)
                }
            }
        }
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingConfirmationAction ?: return
        _uiState.update {
            it.copy(
                pendingConfirmationAction = null,
                pendingConfirmationPrompt = null,
                isAwaitingVoiceConfirmation = false
            )
        }

        viewModelScope.launch {
            val result = app.executeAgentActionUseCase.execute(action, userApproved = true)
            when (result) {
                is ActionResult.Success -> {
                    _uiState.update { it.copy(statusMessage = result.message) }
                    textToSpeech.speak(result.message)
                }
                is ActionResult.Failure -> {
                    _uiState.update { it.copy(statusMessage = result.error) }
                    textToSpeech.speak(result.error)
                }
                else -> Unit
            }
        }
    }

    fun dismissPendingAction() {
        _uiState.update {
            it.copy(
                pendingConfirmationAction = null,
                pendingConfirmationPrompt = null,
                isAwaitingVoiceConfirmation = false,
                statusMessage = "Action cancelled"
            )
        }
        textToSpeech.speak("Action cancelled.")
    }

    // Theme controls
    fun setThemeMode(mode: ThemeMode) {
        app.preferencesManager.themeMode = mode
        _themeMode.value = mode
    }

    fun toggleThemeMode() {
        val nextMode = when (_themeMode.value) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
        }
        setThemeMode(nextMode)
    }

    // Hands-Free & Background controls
    fun setHandsFreeMode(enabled: Boolean) {
        app.preferencesManager.isHandsFreeModeEnabled = enabled
        _isHandsFreeEnabled.value = enabled
    }

    fun setBackgroundListening(enabled: Boolean) {
        app.preferencesManager.isBackgroundListeningEnabled = enabled
        _isBackgroundListening.value = enabled
        if (enabled) {
            NovaWakeWordService.startService(app)
        } else {
            NovaWakeWordService.stopService(app)
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = granted) }
        if (granted) {
            wakeWordDetector.startListening()
            if (_isBackgroundListening.value) {
                NovaWakeWordService.startService(app)
            }
        }
    }

    fun updateApiKeyStatus() {
        _uiState.update { it.copy(isApiKeySet = app.preferencesManager.isApiKeyConfigured) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.repository.clearConversations()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        textToSpeech.shutdown()
        wakeWordDetector.stopListening()
    }
}
