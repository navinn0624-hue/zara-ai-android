package com.assistant.nova.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "USER" or "ASSISTANT"
    val message: String,
    val language: String,
    val actionType: String? = null,
    val actionPayloadJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
