package com.heartline.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.ConversationSummaryEntity
import com.heartline.ai.data.local.entities.MemoryEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.PersonaMoodStateEntity
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.data.local.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    fun observeUser(id: String = "local-user"): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getUser(id: String = "local-user"): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserProfileEntity)
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM persona_profiles ORDER BY createdAt ASC")
    fun observePersonas(): Flow<List<PersonaProfileEntity>>

    @Query("SELECT * FROM persona_profiles ORDER BY createdAt ASC")
    suspend fun getPersonas(): List<PersonaProfileEntity>

    @Query("SELECT * FROM persona_profiles WHERE id = :id LIMIT 1")
    suspend fun getPersona(id: String): PersonaProfileEntity?

    @Query("SELECT COUNT(*) FROM persona_profiles")
    suspend fun countPersonas(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(personas: List<PersonaProfileEntity>)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun observeThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    fun observeThread(threadId: String): Flow<ChatThreadEntity?>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThread(threadId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE personaId = :personaId LIMIT 1")
    suspend fun getThreadForPersona(personaId: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: ChatThreadEntity)

    @Update
    suspend fun updateThread(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET unreadCount = 0 WHERE id = :threadId")
    suspend fun markRead(threadId: String)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessages(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(threadId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun clearMessages(threadId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE personaId = :personaId AND isPinned = 1 AND isArchived = 0 ORDER BY importance DESC")
    suspend fun getPinned(personaId: String): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE personaId = :personaId AND isArchived = 0 " +
            "AND (content LIKE '%' || :query || '%' OR type LIKE '%' || :query || '%') " +
            "ORDER BY isPinned DESC, importance DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun search(personaId: String, query: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE personaId = :personaId AND isArchived = 0 ORDER BY isPinned DESC, importance DESC, updatedAt DESC")
    fun observeMemories(personaId: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("UPDATE memories SET lastUsedAt = :timestamp, useCount = useCount + 1 WHERE id = :memoryId")
    suspend fun updateUse(memoryId: String, timestamp: Long)

    @Query("UPDATE memories SET isPinned = :pinned, updatedAt = :timestamp WHERE id = :memoryId")
    suspend fun setPinned(memoryId: String, pinned: Boolean, timestamp: Long)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun delete(memoryId: String)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM conversation_summaries WHERE threadId = :threadId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(threadId: String): ConversationSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ConversationSummaryEntity)
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM persona_mood_states WHERE personaId = :personaId LIMIT 1")
    suspend fun getMood(personaId: String): PersonaMoodStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mood: PersonaMoodStateEntity)
}
