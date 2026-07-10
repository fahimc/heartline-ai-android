package com.heartline.ai.data.repository

import com.heartline.ai.ai.AiModelProvider
import com.heartline.ai.ai.MemoryExtractor
import com.heartline.ai.ai.MemoryRetriever
import com.heartline.ai.ai.RelationshipEngine
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
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.data.local.entities.UserProfileEntity
import com.heartline.ai.data.seed.PersonaSeedData
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.AiReply
import com.heartline.ai.domain.model.ChatRow
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.notifications.NotificationHelper
import com.heartline.ai.notifications.ProactiveMessageScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import java.util.UUID

class UserRepository(
    private val userDao: UserDao,
    private val settingsStore: UserPreferencesDataStore
) {
    val user: Flow<UserProfileEntity?> = userDao.observeUser()
    val settings: Flow<AppSettings> = settingsStore.settings

    suspend fun saveProfile(
        displayName: String,
        preferredTone: String,
        notificationLevel: String,
        quietStart: String,
        quietEnd: String
    ) {
        userDao.upsert(
            UserProfileEntity(
                displayName = displayName.ifBlank { "Friend" },
                preferredTone = preferredTone,
                notificationLevel = notificationLevel,
                quietHoursStart = quietStart,
                quietHoursEnd = quietEnd
            )
        )
        settingsStore.setOnboardingComplete(true)
    }

    suspend fun getUser(): UserProfileEntity? = userDao.getUser()
    suspend fun updateAiSettings(provider: String, length: String, memory: String) =
        settingsStore.updateAiSettings(provider, length, memory)
    suspend fun updateAppearance(theme: String, wallpaper: String, bubble: String) =
        settingsStore.updateAppearance(theme, wallpaper, bubble)
}

class PersonaRepository(private val personaDao: PersonaDao, private val moodDao: MoodDao) {
    val personas: Flow<List<PersonaProfileEntity>> = personaDao.observePersonas()

    suspend fun seedIfNeeded() {
        if (personaDao.countPersonas() == 0) {
            personaDao.upsertAll(PersonaSeedData.personas())
            PersonaSeedData.moodStates().forEach { moodDao.upsert(it) }
        }
    }

    suspend fun getPersona(id: String): PersonaProfileEntity? = personaDao.getPersona(id)
    suspend fun getMood(personaId: String) = moodDao.getMood(personaId)
}

class ChatRepository(
    private val chatDao: ChatDao,
    private val personaDao: PersonaDao,
    private val relationshipEngine: RelationshipEngine
) {
    val chatRows: Flow<List<ChatRow>> = combine(chatDao.observeThreads(), personaDao.observePersonas()) { threads, personas ->
        val personaMap = personas.associateBy { it.id }
        threads.mapNotNull { thread -> personaMap[thread.personaId]?.let { ChatRow(it, thread) } }
    }

    suspend fun connectPersona(personaId: String): ChatThreadEntity {
        chatDao.getThreadForPersona(personaId)?.let { return it }
        val thread = ChatThreadEntity(
            id = UUID.randomUUID().toString(),
            personaId = personaId,
            lastMessage = "Connection made. Say hello when you are ready."
        )
        chatDao.upsertThread(thread)
        return thread
    }

    fun observeThread(threadId: String) = chatDao.observeThread(threadId)
    fun observeMessages(threadId: String) = chatDao.observeMessages(threadId)
    suspend fun getThread(threadId: String) = chatDao.getThread(threadId)
    suspend fun getRecentMessages(threadId: String, limit: Int = 16) = chatDao.getRecentMessages(threadId, limit).reversed()
    suspend fun markRead(threadId: String) = chatDao.markRead(threadId)

    suspend fun addUserMessage(thread: ChatThreadEntity, content: String): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            threadId = thread.id,
            senderType = "USER",
            content = content,
            source = "USER_SENT"
        )
        chatDao.insertMessage(message)
        val progressed = relationshipEngine.afterUserMessage(thread)
            .copy(lastMessage = content, updatedAt = System.currentTimeMillis())
        chatDao.updateThread(progressed)
        return message
    }

    suspend fun addAiMessage(thread: ChatThreadEntity, content: String, proactive: Boolean = false): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            threadId = thread.id,
            senderType = "AI",
            content = content,
            source = if (proactive) "PROACTIVE" else "AI_REPLY"
        )
        chatDao.insertMessage(message)
        val current = chatDao.getThread(thread.id) ?: thread
        chatDao.updateThread(
            current.copy(
                lastMessage = content,
                updatedAt = System.currentTimeMillis(),
                unreadCount = if (proactive) current.unreadCount + 1 else current.unreadCount
            )
        )
        return message
    }

    suspend fun clearThread(threadId: String) = chatDao.clearMessages(threadId)
}

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val summaryDao: SummaryDao,
    private val retriever: MemoryRetriever,
    private val extractor: MemoryExtractor
) {
    fun observeMemories(personaId: String): Flow<List<MemoryEntity>> = memoryDao.observeMemories(personaId)
    suspend fun getPinnedMemories(personaId: String): List<MemoryEntity> = memoryDao.getPinned(personaId)

    suspend fun searchRelevantMemories(personaId: String, query: String, limit: Int): List<MemoryEntity> {
        val direct = memoryDao.search(personaId, query, limit * 2)
        return direct.sortedByDescending { retriever.score(it, query) }.take(limit).also { memories ->
            memories.forEach { memoryDao.updateUse(it.id, System.currentTimeMillis()) }
        }
    }

    suspend fun getRecentConversationSummary(threadId: String): ConversationSummaryEntity? =
        summaryDao.getLatest(threadId)

    suspend fun saveMemoryCandidates(personaId: String, candidates: List<MemoryCandidate>) {
        val existing = memoryDao.search(personaId, "", 200).map { it.content }
        extractor.mergeDuplicates(existing, candidates).forEach { candidate ->
            memoryDao.upsert(
                MemoryEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    userId = "local-user",
                    type = candidate.type,
                    content = candidate.content,
                    importance = candidate.importance.coerceIn(1, 10),
                    confidence = candidate.confidence,
                    embeddingText = candidate.content,
                    isSensitive = candidate.isSensitive
                )
            )
        }
    }

    suspend fun updateMemoryUse(memoryId: String) = memoryDao.updateUse(memoryId, System.currentTimeMillis())
    suspend fun setPinned(memoryId: String, pinned: Boolean) = memoryDao.setPinned(memoryId, pinned, System.currentTimeMillis())
    suspend fun deleteMemory(memoryId: String) = memoryDao.delete(memoryId)
    suspend fun clearAllMemories() = memoryDao.clearAll()
    suspend fun decayOldMemories() = Unit
}

class AiRepository(
    private val provider: AiModelProvider,
    private val userRepository: UserRepository,
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository
) {
    suspend fun generateReply(thread: ChatThreadEntity, userMessage: String): AiReply {
        val persona = personaRepository.getPersona(thread.personaId) ?: error("Missing persona")
        val memories = memoryRepository.getPinnedMemories(persona.id) +
            memoryRepository.searchRelevantMemories(persona.id, userMessage, 5)
        val request = AiChatRequest(
            persona = persona,
            user = userRepository.getUser(),
            thread = thread,
            mood = personaRepository.getMood(persona.id),
            memories = memories.distinctBy { it.id },
            recentMessages = chatRepository.getRecentMessages(thread.id, 18),
            message = userMessage
        )
        var raw = ""
        provider.generateReply(request).collect { raw += it }
        return parseReply(raw)
    }

    suspend fun extractMemories(personaId: String, messages: List<MessageEntity>) {
        memoryRepository.saveMemoryCandidates(personaId, provider.extractMemories(messages))
    }

    private fun parseReply(raw: String): AiReply = runCatching {
        val json = JSONObject(raw)
        val messagesJson = json.getJSONArray("messages")
        val messages = (0 until messagesJson.length()).map { messagesJson.getString(it) }
        val candidatesJson = json.optJSONArray("memory_candidates")
        val candidates = if (candidatesJson == null) {
            emptyList()
        } else {
            (0 until candidatesJson.length()).map { index ->
                val item = candidatesJson.getJSONObject(index)
                MemoryCandidate(
                    type = item.optString("type", "fact"),
                    content = item.optString("content"),
                    importance = item.optInt("importance", 5)
                )
            }.filter { it.content.isNotBlank() }
        }
        AiReply(messages = messages, mood = json.optString("mood", "soft"), memoryCandidates = candidates)
    }.getOrElse {
        AiReply(messages = listOf("I am here. Tell me that again, a little slower?"), mood = "soft", memoryCandidates = emptyList())
    }
}

class NotificationRepository(
    private val scheduler: ProactiveMessageScheduler,
    private val notificationHelper: NotificationHelper
) {
    fun scheduleProactiveMessages() = scheduler.schedule()

    fun showCompanionMessage(threadId: String, personaName: String, message: String) {
        notificationHelper.showMessage(threadId, personaName, message)
    }
}
