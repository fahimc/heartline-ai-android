package com.heartline.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.heartline.ai.data.local.dao.ChatDao
import com.heartline.ai.data.local.dao.MemoryDao
import com.heartline.ai.data.local.dao.MoodDao
import com.heartline.ai.data.local.dao.PersonaDao
import com.heartline.ai.data.local.dao.SummaryDao
import com.heartline.ai.data.local.dao.UserDao
import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.ConversationSummaryEntity
import com.heartline.ai.data.local.entities.MemoryEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.PersonaMoodStateEntity
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.data.local.entities.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        PersonaProfileEntity::class,
        ChatThreadEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ConversationSummaryEntity::class,
        PersonaMoodStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun personaDao(): PersonaDao
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heartline.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
