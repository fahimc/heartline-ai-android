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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

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
    suspend fun updateNotificationLevel(level: String) {
        userDao.getUser()?.let { userDao.upsert(it.copy(notificationLevel = level)) }
    }
    suspend fun updateAiSettings(provider: String, length: String, memory: String) =
        settingsStore.updateAiSettings(provider, length, memory)
    suspend fun updateAppearance(theme: String, wallpaper: String, bubble: String) =
        settingsStore.updateAppearance(theme, wallpaper, bubble)
}

class PersonaRepository(private val personaDao: PersonaDao, private val moodDao: MoodDao) {
    val personas: Flow<List<PersonaProfileEntity>> = personaDao.observePersonas()

    suspend fun seedIfNeeded() {
        val firstSeed = personaDao.countPersonas() == 0
        personaDao.upsertAll(PersonaSeedData.personas())
        if (firstSeed) {
            PersonaSeedData.moodStates().forEach { moodDao.upsert(it) }
        }
    }

    suspend fun getPersona(id: String): PersonaProfileEntity? = personaDao.getPersona(id)
    suspend fun getMood(personaId: String) = moodDao.getMood(personaId)

    suspend fun updateMood(personaId: String, mood: String, affinity: Int, proactive: Boolean = false) {
        val now = System.currentTimeMillis()
        val current = moodDao.getMood(personaId)
        moodDao.upsert(
            (current ?: com.heartline.ai.data.local.entities.PersonaMoodStateEntity(
                personaId = personaId,
                mood = mood,
                energyLevel = 60,
                affectionLevel = 10,
                lastInteractionAt = now
            )).copy(
                mood = mood,
                energyLevel = when (mood) {
                    "sleepy" -> 32
                    "happy", "playful", "flirty" -> 76
                    "concerned" -> 48
                    else -> 60
                },
                affectionLevel = max(current?.affectionLevel ?: 10, affinity.coerceIn(0, 100)),
                lastInteractionAt = now,
                lastProactiveMessageAt = if (proactive) now else current?.lastProactiveMessageAt ?: 0L
            )
        )
    }
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
    suspend fun getThreads() = chatDao.getThreads()
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

    suspend fun clearThread(threadId: String) {
        chatDao.clearMessages(threadId)
        chatDao.getThread(threadId)?.let {
            chatDao.updateThread(it.copy(lastMessage = "", unreadCount = 0, updatedAt = System.currentTimeMillis()))
        }
    }
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
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
            .toSet()
        val candidates = memoryDao.search(personaId, "", 200).filter { memory ->
            memory.isPinned || tokens.any { token -> token in memory.content.lowercase() }
        }
        return candidates.sortedByDescending { retriever.score(it, query) }.take(limit).also { memories ->
            memories.forEach { memoryDao.updateUse(it.id, System.currentTimeMillis()) }
        }
    }

    suspend fun getRecentConversationSummary(threadId: String): ConversationSummaryEntity? =
        summaryDao.getLatest(threadId)

    suspend fun saveMemoryCandidates(personaId: String, candidates: List<MemoryCandidate>) {
        val existing = memoryDao.search(personaId, "", 200)
        val now = System.currentTimeMillis()
        candidates.forEach { candidate ->
            val normalized = candidate.content.normalizeMemory()
            val duplicate = existing.firstOrNull { it.content.normalizeMemory() == normalized }
            if (duplicate != null) {
                memoryDao.upsert(
                    duplicate.copy(
                        importance = max(duplicate.importance, candidate.importance).plus(1).coerceAtMost(10),
                        confidence = max(duplicate.confidence, candidate.confidence),
                        updatedAt = now,
                        isSensitive = duplicate.isSensitive || candidate.isSensitive
                    )
                )
            } else {
                memoryDao.upsert(MemoryEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = personaId,
                    userId = "local-user",
                    type = candidate.type,
                    content = candidate.content,
                    importance = candidate.importance.coerceIn(1, 10),
                    confidence = candidate.confidence,
                    embeddingText = candidate.content,
                    isSensitive = candidate.isSensitive,
                    createdAt = now,
                    updatedAt = now
                ))
            }
        }
    }

    suspend fun saveConversationSummary(threadId: String, summary: com.heartline.ai.domain.model.ConversationSummary) {
        if (summary.summary.isBlank()) return
        summaryDao.upsert(
            ConversationSummaryEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                summary = summary.summary,
                startMessageId = summary.startMessageId,
                endMessageId = summary.endMessageId
            )
        )
    }

    suspend fun updateMemoryUse(memoryId: String) = memoryDao.updateUse(memoryId, System.currentTimeMillis())
    suspend fun setPinned(memoryId: String, pinned: Boolean) = memoryDao.setPinned(memoryId, pinned, System.currentTimeMillis())
    suspend fun deleteMemory(memoryId: String) = memoryDao.delete(memoryId)
    suspend fun clearAllMemories() = memoryDao.clearAll()
    suspend fun decayOldMemories() = Unit

    private fun String.normalizeMemory(): String = lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
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
            memoryRepository.searchRelevantMemories(persona.id, userMessage, 3)
        val request = AiChatRequest(
            persona = persona,
            user = userRepository.getUser(),
            thread = thread,
            mood = personaRepository.getMood(persona.id),
            memories = memories.distinctBy { it.id }.take(6),
            recentMessages = chatRepository.getRecentMessages(thread.id, 20),
            conversationSummary = memoryRepository.getRecentConversationSummary(thread.id)?.summary.orEmpty(),
            message = userMessage
        )
        var raw = ""
        provider.generateReply(request).collect { raw += it }
        return parseReply(raw).also { reply ->
            personaRepository.updateMood(persona.id, reply.mood, thread.affinityScore)
        }
    }

    suspend fun generateProactiveReply(thread: ChatThreadEntity): AiReply {
        val persona = personaRepository.getPersona(thread.personaId) ?: error("Missing persona")
        val summary = memoryRepository.getRecentConversationSummary(thread.id)?.summary.orEmpty()
        val memories = memoryRepository.getPinnedMemories(persona.id) +
            memoryRepository.searchRelevantMemories(persona.id, thread.lastMessage, 4)
        val recentMessages = chatRepository.getRecentMessages(thread.id, 20)
        val routine = com.heartline.ai.ai.RoutineEngine()
        val raw = provider.generateProactiveMessage(
            com.heartline.ai.domain.model.ProactiveMessageRequest(
                persona = persona,
                user = userRepository.getUser(),
                thread = thread,
                mood = personaRepository.getMood(persona.id),
                memories = memories.distinctBy { it.id }.take(6),
                timeOfDay = routine.timeOfDay(),
                lastInteraction = thread.updatedAt.toString(),
                recentMessages = recentMessages,
                conversationSummary = summary
            )
        )
        return parseReply(raw).also { reply ->
            personaRepository.updateMood(persona.id, reply.mood, thread.affinityScore, proactive = true)
        }
    }

    suspend fun extractMemories(personaId: String, messages: List<MessageEntity>) {
        memoryRepository.saveMemoryCandidates(personaId, provider.extractMemories(messages))
    }

    suspend fun refreshConversationSummary(threadId: String) {
        val messages = chatRepository.getRecentMessages(threadId, 48)
        if (messages.size < 12) return
        memoryRepository.saveConversationSummary(threadId, provider.summarizeConversation(messages))
    }

    private fun parseReply(raw: String): AiReply {
        val cleaned = raw.cleanupModelText()
        val json = cleaned.extractJsonObject()
        val messages = (json?.extractMessages() ?: cleaned.extractMessagesArray())
            .mapNotNull { it.cleanupMessageBubble().takeIf(String::isNotBlank) }
            .filterNot { it.isSchemaNoise() }
            .take(3)
            .ifEmpty { cleaned.plainTextBubbles() }
            .ifEmpty { listOf("I lost my train of thought for a second. Tell me that again?") }
        val candidatesJson = json?.optJSONArray("memory_candidates")
        val candidates = if (candidatesJson == null) {
            emptyList()
        } else {
            (0 until candidatesJson.length()).map { index ->
                val item = candidatesJson.getJSONObject(index)
                MemoryCandidate(
                    type = item.optString("type", "fact"),
                    content = item.optString("content"),
                    importance = item.optInt("importance", 5),
                    confidence = item.optDouble("confidence", 0.8).toFloat(),
                    isSensitive = item.optBoolean("isSensitive", false)
                )
            }.filter { it.content.isNotBlank() }
        }
        val mood = json?.optString("mood", "soft") ?: "curious"
        return AiReply(
            messages = messages,
            mood = mood,
            emotion = json?.optString("emotion", mood) ?: mood,
            animation = json?.optString("animation", "soft_smile") ?: "soft_smile",
            memoryCandidates = candidates
        )
    }

    private fun String.extractJsonObject(): JSONObject? = runCatching {
        val start = indexOf('{')
        if (start == -1) return@runCatching null
        val end = lastIndexOf('}')
        val candidate = if (end > start) substring(start, end + 1) else "${substring(start)}}"
        JSONObject(candidate)
    }.getOrNull()

    private fun JSONObject.extractMessages(): List<String> {
        val messagesJson = optJSONArray("messages")
        return if (messagesJson == null) {
            listOfNotNull(optString("message").takeIf { it.isNotBlank() })
        } else {
            messagesJson.toStringList()
        }
    }

    private fun String.extractMessagesArray(): List<String> {
        val keyIndex = indexOf("\"messages\"")
        if (keyIndex == -1) return emptyList()
        val start = indexOf('[', keyIndex)
        if (start == -1) return emptyList()
        val matchedEnd = findMatchingArrayEnd(start)
        val end = if (matchedEnd != null && matchedEnd > start) matchedEnd else lastIndexOf(']').takeIf { it > start }
        if (end == null || end <= start) return emptyList()
        val candidate = substring(start, end + 1)
        return runCatching { JSONArray(candidate).toStringList() }
            .getOrElse { candidate.extractQuotedStrings() }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

    private fun String.extractQuotedStrings(): List<String> =
        Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(this).mapNotNull { match ->
            runCatching { JSONArray("[\"${match.groupValues[1]}\"]").optString(0) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }.toList()

    private fun String.findMatchingArrayEnd(start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\' && inString) {
                escaped = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (char) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun String.cleanupMessageBubble(): String =
        cleanupModelText()
            .trim()
            .trim(',', '[', ']', '{', '}', '"')
            .trim()

    private fun String.isSchemaNoise(): Boolean {
        val normalized = lowercase().trim()
        return normalized in setOf(
            "first bubble",
            "second bubble",
            "third bubble",
            "fourth bubble",
            "one short message",
            "actual reply text",
            "..."
        ) || normalized.startsWith("messages") ||
            normalized.startsWith("memory_candidates") ||
            normalized.startsWith("mood")
    }

    private fun String.plainTextFallback(): String? {
        if (startsWith("{") || contains("\"messages\"")) return null
        return cleanupMessageBubble().takeIf { it.isNotBlank() }
    }

    private fun String.plainTextBubbles(): List<String> {
        if (startsWith("{") || contains("\"messages\"")) return emptyList()
        val cleaned = cleanupMessageBubble()
            .replace(Regex("(?i)^assistant\\s*[:\\-]\\s*"), "")
            .trim()
        if (cleaned.isBlank()) return emptyList()
        val lineBubbles = cleaned
            .lines()
            .map { it.trim().trim('-', '•', '*').trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.isSchemaNoise() }
        val source = if (lineBubbles.size > 1) lineBubbles else cleaned.split(Regex("(?<=[.!?])\\s+"))
        return source
            .map { it.cleanupMessageBubble() }
            .filter { it.isNotBlank() }
            .filterNot { it.isSchemaNoise() }
            .take(2)
    }

    private fun String.cleanupModelText(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .replace(Regex("(?is)^\\s*thinking\\s*[:\\-].*?(?:\\n\\s*answer\\s*[:\\-]|\\z)"), "")
            .removePrefix("/no_think")
            .trim()
}

class NotificationRepository(
    private val scheduler: ProactiveMessageScheduler,
    private val notificationHelper: NotificationHelper
) {
    fun scheduleProactiveMessages() = scheduler.schedule()
    fun cancelProactiveMessages() = scheduler.cancel()

    fun showCompanionMessage(threadId: String, personaName: String, message: String) {
        notificationHelper.showMessage(threadId, personaName, message)
    }
}
