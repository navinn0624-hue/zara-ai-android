package com.assistant.nova.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionName: String,
    val details: String,
    val status: String, // "SUCCESS", "CONFIRMED", "CANCELLED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)
