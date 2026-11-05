package com.assistant.nova.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memory")
data class UserMemoryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
