package com.assistant.nova.domain.usecase

import com.assistant.nova.data.repository.AssistantRepository
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.domain.model.ChatMessage
import com.assistant.nova.domain.model.MessageSender

data class VoiceProcessResult(
    val replyText: String,
    val detectedLanguage: String,
    val action: AgentAction? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

class ProcessVoiceInputUseCase(
    private val repository: AssistantRepository,
    private val detectLanguageUseCase: DetectLanguageUseCase,
    private val manageMemoryUseCase: ManageMemoryUseCase
) {

    suspend fun processInput(userSpeech: String): VoiceProcessResult {
        if (userSpeech.isBlank()) {
            return VoiceProcessResult(
                replyText = "I didn't catch that. Could you repeat?",
                detectedLanguage = "en",
                isSuccess = false
            )
        }

        // 1. Detect language of input
        val userLang = detectLanguageUseCase.detectLanguage(userSpeech)

        // 2. Persist user turn
        repository.saveConversation(
            sender = "USER",
            message = userSpeech,
            language = userLang
        )

        // 3. Query Gemini AI with context
        val geminiResponse = repository.queryAssistant(userSpeech)

        if (!geminiResponse.isSuccess) {
            repository.saveConversation(
                sender = "ASSISTANT",
                message = geminiResponse.replyText,
                language = "en"
            )
            return VoiceProcessResult(
                replyText = geminiResponse.replyText,
                detectedLanguage = "en",
                isSuccess = false,
                errorMessage = geminiResponse.errorMessage
            )
        }

        // 4. Detect language of reply
        val replyLang = detectLanguageUseCase.detectLanguage(geminiResponse.replyText)

        // 5. Persist assistant turn with action metadata if present
        val actionType = geminiResponse.detectedAction?.let { it::class.java.simpleName }
        repository.saveConversation(
            sender = "ASSISTANT",
            message = geminiResponse.replyText,
            language = replyLang,
            actionType = actionType
        )

        return VoiceProcessResult(
            replyText = geminiResponse.replyText,
            detectedLanguage = replyLang,
            action = geminiResponse.detectedAction,
            isSuccess = true
        )
    }
}
