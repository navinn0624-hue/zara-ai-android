package com.assistant.nova.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.assistant.nova.data.local.dao.ActionLogDao
import com.assistant.nova.data.local.dao.ConversationDao
import com.assistant.nova.data.local.dao.UserMemoryDao
import com.assistant.nova.data.local.entity.ActionLogEntity
import com.assistant.nova.data.local.entity.ConversationEntity
import com.assistant.nova.data.local.entity.UserMemoryEntity

@Database(
    entities = [
        ConversationEntity::class,
        UserMemoryEntity::class,
        ActionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NovaDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun actionLogDao(): ActionLogDao

    companion object {
        @Volatile
        private var INSTANCE: NovaDatabase? = null

        fun getInstance(context: Context): NovaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova_assistant.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
