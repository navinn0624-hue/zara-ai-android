package com.assistant.nova.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assistant.nova.data.local.entity.UserMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemoryEntity)

    @Query("SELECT * FROM user_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): UserMemoryEntity?

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    suspend fun getAllMemories(): List<UserMemoryEntity>

    @Query("DELETE FROM user_memory WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String)

    @Delete
    suspend fun deleteMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memory")
    suspend fun clearAll()
}
