package com.assistant.nova.domain.usecase

import com.assistant.nova.data.local.entity.UserMemoryEntity
import com.assistant.nova.data.repository.AssistantRepository
import kotlinx.coroutines.flow.Flow

class ManageMemoryUseCase(
    private val repository: AssistantRepository
) {

    fun getMemoriesFlow(): Flow<List<UserMemoryEntity>> = repository.getAllMemoriesFlow()

    suspend fun getAllMemories(): List<UserMemoryEntity> = repository.getAllMemories()

    suspend fun saveMemory(key: String, value: String) {
        if (key.isNotBlank() && value.isNotBlank()) {
            repository.saveMemory(key.trim(), value.trim())
        }
    }

    suspend fun deleteMemory(key: String) {
        repository.deleteMemory(key)
    }

    suspend fun clearAll() {
        repository.clearMemories()
    }
}
