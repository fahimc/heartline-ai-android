package com.heartline.ai.domain.model

import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.MemoryEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.PersonaMoodStateEntity
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.data.local.entities.UserProfileEntity

data class AiChatRequest(
    val persona: PersonaProfileEntity,
    val user: UserProfileEntity?,
    val thread: ChatThreadEntity,
    val mood: PersonaMoodStateEntity?,
    val memories: List<MemoryEntity>,
    val recentMessages: List<MessageEntity>,
    val message: String
)

data class ProactiveMessageRequest(
    val persona: PersonaProfileEntity,
    val user: UserProfileEntity?,
    val thread: ChatThreadEntity,
    val mood: PersonaMoodStateEntity?,
    val memories: List<MemoryEntity>,
    val timeOfDay: String,
    val lastInteraction: String
)

data class MemoryCandidate(
    val type: String,
    val content: String,
    val importance: Int,
    val confidence: Float = 0.8f,
    val isSensitive: Boolean = false
)

data class ConversationSummary(
    val summary: String,
    val startMessageId: String,
    val endMessageId: String
)

data class AiReply(
    val messages: List<String>,
    val mood: String,
    val memoryCandidates: List<MemoryCandidate>,
    val emotion: String = mood,
    val animation: String = "soft_smile"
)

data class PersonaWithThread(
    val persona: PersonaProfileEntity,
    val thread: ChatThreadEntity
)

data class ChatRow(
    val persona: PersonaProfileEntity,
    val thread: ChatThreadEntity
)
