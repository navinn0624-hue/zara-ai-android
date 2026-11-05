package com.assistant.nova.data.remote.dto

import com.assistant.nova.domain.model.AgentAction

data class GeminiResponse(
    val replyText: String,
    val detectedAction: AgentAction? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
