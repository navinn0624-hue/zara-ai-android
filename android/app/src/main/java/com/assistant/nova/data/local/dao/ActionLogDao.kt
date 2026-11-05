package com.assistant.nova.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.assistant.nova.data.local.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {

    @Insert
    suspend fun insertLog(log: ActionLogEntity): Long

    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActionLogEntity>>

    @Query("DELETE FROM action_logs")
    suspend fun clearLogs()
}
