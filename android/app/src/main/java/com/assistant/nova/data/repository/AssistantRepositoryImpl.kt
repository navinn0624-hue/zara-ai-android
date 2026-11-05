package com.assistant.nova.data.repository

import com.assistant.nova.data.local.dao.ActionLogDao
import com.assistant.nova.data.local.dao.ConversationDao
import com.assistant.nova.data.local.dao.UserMemoryDao
import com.assistant.nova.data.local.entity.ActionLogEntity
import com.assistant.nova.data.local.entity.ConversationEntity
import com.assistant.nova.data.local.entity.UserMemoryEntity
import com.assistant.nova.data.remote.GeminiApiClient
import com.assistant.nova.data.remote.dto.GeminiResponse
import kotlinx.coroutines.flow.Flow

class AssistantRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val userMemoryDao: UserMemoryDao,
    private val actionLogDao: ActionLogDao,
    private val geminiApiClient: GeminiApiClient
) : AssistantRepository {

    override fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    override suspend fun getRecentConversations(limit: Int): List<ConversationEntity> {
        return conversationDao.getRecentConversations(limit)
    }

    override suspend fun saveConversation(
        sender: String,
        message: String,
        language: String,
        actionType: String?,
        payloadJson: String?
    ): Long {
        return conversationDao.insert(
            ConversationEntity(
                sender = sender,
                message = message,
                language = language,
                actionType = actionType,
                actionPayloadJson = payloadJson
            )
        )
    }

    override suspend fun clearConversations() {
        conversationDao.clearAll()
    }

    override fun getAllMemoriesFlow(): Flow<List<UserMemoryEntity>> {
        return userMemoryDao.getAllMemoriesFlow()
    }

    override suspend fun getAllMemories(): List<UserMemoryEntity> {
        return userMemoryDao.getAllMemories()
    }

    override suspend fun saveMemory(key: String, value: String) {
        userMemoryDao.saveMemory(UserMemoryEntity(key = key, value = value))
    }

    override suspend fun deleteMemory(key: String) {
        userMemoryDao.deleteMemoryByKey(key)
    }

    override suspend fun clearMemories() {
        userMemoryDao.clearAll()
    }

    override fun getAllActionLogs(): Flow<List<ActionLogEntity>> {
        return actionLogDao.getAllLogs()
    }

    override suspend fun logAction(actionName: String, details: String, status: String) {
        actionLogDao.insertLog(
            ActionLogEntity(
                actionName = actionName,
                details = details,
                status = status
            )
        )
    }

    override suspend fun queryAssistant(userPrompt: String): GeminiResponse {
        val recentEntities = conversationDao.getRecentConversations(6)
        val historyPairs = recentEntities.reversed().map {
            (if (it.sender == "USER") "user" else "model") to it.message
        }

        val memoryEntities = userMemoryDao.getAllMemories()
        val memoryPairs = memoryEntities.map { it.key to it.value }

        return geminiApiClient.generateResponse(
            userPrompt = userPrompt,
            recentHistory = historyPairs,
            memories = memoryPairs
        )
    }
}
