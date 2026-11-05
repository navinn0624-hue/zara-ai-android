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
import com.assistant.nova.ui.components.OrbState
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
    val statusMessage: String = "Tap orb or mic to speak",
    val isApiKeySet: Boolean = false
)

class NovaViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovaApplication
    private val speechRecognizer = NovaSpeechRecognizer(app)
    private val textToSpeech = NovaTextToSpeech(app, app.preferencesManager)
    private val wakeWordDetector = WakeWordDetector(app, app.preferencesManager)

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
        // Collect SpeechRecognizer RMS decibels for dynamic Orb and Waveform
        viewModelScope.launch {
            speechRecognizer.rmsAmplitude.collect { rms ->
                _uiState.update { it.copy(rmsAmplitude = rms) }
            }
        }

        // Collect SpeechRecognizer state
        viewModelScope.launch {
            speechRecognizer.state.collect { state ->
                when (state) {
                    is SpeechState.Listening -> {
                        _uiState.update {
                            it.copy(
                                orbState = OrbState.LISTENING,
                                statusMessage = "Listening… (Tamil & English)"
                            )
                        }
                    }
                    is SpeechState.Processing -> {
                        _uiState.update {
                            it.copy(
                                orbState = OrbState.THINKING,
                                statusMessage = "Processing speech…"
                            )
                        }
                    }
                    is SpeechState.Idle -> {
                        if (!_uiState.value.isSpeaking && _uiState.value.orbState != OrbState.THINKING) {
                            _uiState.update {
                                it.copy(
                                    orbState = OrbState.IDLE,
                                    statusMessage = "Tap orb or mic to speak"
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

        // Collect TTS Speaking state
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

        // Wake word callback
        wakeWordDetector.onWakeWordDetected = {
            onMicTrigger()
        }
    }

    private fun setupSpeechCallbacks() {
        speechRecognizer.onPartialSpeechResult = { partial ->
            _uiState.update { it.copy(liveTranscript = partial) }
        }

        speechRecognizer.onSpeechResult = { finalUtterance ->
            _uiState.update { it.copy(liveTranscript = finalUtterance) }
            submitUserSpeech(finalUtterance)
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
            textToSpeech.speak(result.replyText)

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
                        pendingConfirmationPrompt = actionResult.prompt
                    )
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

    fun confirmPendingAction() {
        val action = _uiState.value.pendingConfirmationAction ?: return
        _uiState.update {
            it.copy(
                pendingConfirmationAction = null,
                pendingConfirmationPrompt = null
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
                statusMessage = "Action cancelled"
            )
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = granted) }
        if (granted) {
            wakeWordDetector.startListening()
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
