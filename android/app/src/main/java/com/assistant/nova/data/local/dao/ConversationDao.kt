package com.assistant.nova.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.assistant.nova.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int): List<ConversationEntity>

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}
