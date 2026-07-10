package com.heartline.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String = "local-user",
    val displayName: String,
    val preferredTone: String,
    val notificationLevel: String,
    val quietHoursStart: String,
    val quietHoursEnd: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "persona_profiles")
data class PersonaProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val bio: String,
    val tagline: String,
    val avatarUri: String,
    val personalityJson: String,
    val interestsJson: String,
    val chatStyle: String,
    val affectionStyle: String,
    val humourStyle: String,
    val relationshipPace: String,
    val boundaries: String,
    val systemPromptFragment: String,
    val routineJson: String,
    val proactiveMessageStyle: String,
    val memoryPrioritiesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val unreadCount: Int = 0,
    val relationshipStage: String = "New connection",
    val affinityScore: Int = 5,
    val messageCount: Int = 0,
    val daysKnown: Int = 0,
    val insideJokes: String = "",
    val importantMoments: String = ""
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val senderType: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "SENT",
    val source: String = "USER_SENT"
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val userId: String,
    val type: String,
    val content: String,
    val importance: Int,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0,
    val embeddingText: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isSensitive: Boolean = false
)

@Entity(tableName = "conversation_summaries")
data class ConversationSummaryEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val summary: String,
    val startMessageId: String,
    val endMessageId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "persona_mood_states")
data class PersonaMoodStateEntity(
    @PrimaryKey val personaId: String,
    val mood: String,
    val energyLevel: Int,
    val affectionLevel: Int,
    val lastInteractionAt: Long,
    val lastProactiveMessageAt: Long = 0L
)
