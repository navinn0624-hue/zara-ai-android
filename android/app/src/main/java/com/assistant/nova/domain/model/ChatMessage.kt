package com.assistant.nova.domain.model

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: Long = 0,
    val sender: MessageSender,
    val content: String,
    val detectedLanguage: String = "en",
    val timestamp: Long = System.currentTimeMillis(),
    val action: AgentAction? = null
)
