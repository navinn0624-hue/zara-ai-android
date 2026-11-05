package com.assistant.nova.data.repository

import com.assistant.nova.data.local.entity.ActionLogEntity
import com.assistant.nova.data.local.entity.ConversationEntity
import com.assistant.nova.data.local.entity.UserMemoryEntity
import com.assistant.nova.data.remote.dto.GeminiResponse
import kotlinx.coroutines.flow.Flow

interface AssistantRepository {

    // Conversation
    fun getAllConversations(): Flow<List<ConversationEntity>>
    suspend fun getRecentConversations(limit: Int = 10): List<ConversationEntity>
    suspend fun saveConversation(sender: String, message: String, language: String, actionType: String? = null, payloadJson: String? = null): Long
    suspend fun clearConversations()

    // Memories
    fun getAllMemoriesFlow(): Flow<List<UserMemoryEntity>>
    suspend fun getAllMemories(): List<UserMemoryEntity>
    suspend fun saveMemory(key: String, value: String)
    suspend fun deleteMemory(key: String)
    suspend fun clearMemories()

    // Action Logs
    fun getAllActionLogs(): Flow<List<ActionLogEntity>>
    suspend fun logAction(actionName: String, details: String, status: String)

    // Remote AI
    suspend fun queryAssistant(userPrompt: String): GeminiResponse
}
