package com.heartline.ai.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.heartline.ai.AppContainer
import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.MemoryEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.data.repository.AppSettings
import com.heartline.ai.domain.model.ChatRow
import com.heartline.ai.domain.model.AiReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    val userName = MutableStateFlow("")
    val preferredTone = MutableStateFlow("Sweet")
    val notificationLevel = MutableStateFlow("Normal")
    val quietStart = MutableStateFlow("22:00")
    val quietEnd = MutableStateFlow("07:00")

    fun finish() {
        viewModelScope.launch {
            container.userRepository.saveProfile(
                userName.value,
                preferredTone.value,
                notificationLevel.value,
                quietStart.value,
                quietEnd.value
            )
            if (notificationLevel.value == "Off") {
                container.proactiveMessageScheduler.cancel()
            } else {
                container.proactiveMessageScheduler.schedule()
            }
        }
    }
}

data class DiscoverUiState(
    val personas: List<PersonaProfileEntity> = emptyList(),
    val currentIndex: Int = 0,
    val connectedThread: ChatThreadEntity? = null,
    val selectedProfile: PersonaProfileEntity? = null,
    val unreadCount: Int = 0
) {
    val currentPersona: PersonaProfileEntity? = personas.getOrNull(currentIndex)
}

class DiscoverViewModel(private val container: AppContainer) : ViewModel() {
    private val index = MutableStateFlow(0)
    private val connectedThread = MutableStateFlow<ChatThreadEntity?>(null)
    private val selectedProfile = MutableStateFlow<PersonaProfileEntity?>(null)

    val uiState: StateFlow<DiscoverUiState> = combine(
        container.personaRepository.personas,
        index,
        connectedThread,
        selectedProfile,
        container.chatRepository.chatRows
    ) { personas, currentIndex, thread, profile, chats ->
        DiscoverUiState(
            personas = personas,
            currentIndex = currentIndex.coerceAtMost(personas.lastIndex.coerceAtLeast(0)),
            connectedThread = thread,
            selectedProfile = profile,
            unreadCount = chats.sumOf { it.thread.unreadCount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    fun pass() {
        index.value += 1
    }

    fun rewind() {
        index.value = (index.value - 1).coerceAtLeast(0)
    }

    fun viewProfile(persona: PersonaProfileEntity) {
        selectedProfile.value = persona
    }

    fun closeProfile() {
        selectedProfile.value = null
    }

    fun dismissConnection() {
        connectedThread.value = null
    }

    fun connect(persona: PersonaProfileEntity) {
        viewModelScope.launch {
            val thread = container.chatRepository.connectPersona(persona.id)
            container.proactiveMessageScheduler.scheduleAfterConnection(thread.id)
            connectedThread.value = thread
            index.value += 1
        }
    }
}

class ChatListViewModel(private val container: AppContainer) : ViewModel() {
    val chatRows: StateFlow<List<ChatRow>> = container.chatRepository.chatRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class PersonaProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedPersona = MutableStateFlow<PersonaProfileEntity?>(null)
    val persona: StateFlow<PersonaProfileEntity?> = selectedPersona

    fun load(personaId: String) {
        viewModelScope.launch {
            selectedPersona.value = container.personaRepository.getPersona(personaId)
        }
    }

    fun connect(onConnected: (String) -> Unit) {
        val personaId = selectedPersona.value?.id ?: return
        viewModelScope.launch {
            val thread = container.chatRepository.connectPersona(personaId)
            container.proactiveMessageScheduler.scheduleAfterConnection(thread.id)
            onConnected(thread.id)
        }
    }
}

data class ChatThreadUiState(
    val thread: ChatThreadEntity? = null,
    val persona: PersonaProfileEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val isTyping: Boolean = false,
    val memories: List<MemoryEntity> = emptyList()
)

class ChatThreadViewModel(
    private val container: AppContainer,
    private val threadId: String
) : ViewModel() {
    private val isTyping = MutableStateFlow(false)
    private val personaState = MutableStateFlow<PersonaProfileEntity?>(null)
    private val pendingMessages = Channel<String>(Channel.UNLIMITED)

    val input = MutableStateFlow("")

    val uiState: StateFlow<ChatThreadUiState> = combine(
        container.chatRepository.observeThread(threadId),
        container.chatRepository.observeMessages(threadId),
        isTyping,
        personaState
    ) { thread, messages, typing, persona ->
        ChatThreadUiState(
            thread = thread,
            persona = persona,
            messages = messages,
            isTyping = typing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatThreadUiState())

    init {
        viewModelScope.launch {
            container.chatRepository.markRead(threadId)
            val thread = container.chatRepository.getThread(threadId)
            if (thread != null) {
                personaState.value = container.personaRepository.getPersona(thread.personaId)
            }
        }
        viewModelScope.launch {
            container.chatRepository.observeThread(threadId).collect { thread ->
                if ((thread?.unreadCount ?: 0) > 0) container.chatRepository.markRead(threadId)
            }
        }
        viewModelScope.launch {
            for (message in pendingMessages) {
                try {
                    processMessage(message)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.e("HeartlineAI", "Unexpected chat delivery failure", error)
                    recoverFailedTurn(message)
                }
            }
        }
    }

    fun send() {
        val text = input.value.trim()
        if (text.isBlank()) return
        input.value = ""
        val queued = pendingMessages.trySend(text)
        if (queued.isFailure) {
            input.value = text
            Log.e("HeartlineAI", "Could not queue outgoing message", queued.exceptionOrNull())
        }
    }

    private suspend fun processMessage(text: String) {
        val thread = container.chatRepository.getThread(threadId) ?: return
        container.chatRepository.addUserMessage(thread, text)
        val currentThread = container.chatRepository.getThread(threadId) ?: thread
        val reply = try {
            container.aiRepository.generateReply(currentThread, text)
        } catch (firstError: Throwable) {
            Log.w("HeartlineAI", "Chat turn failed once; retrying", firstError)
            delay(200)
            runCatching { container.aiRepository.generateReply(currentThread, text) }
                .getOrElse { error ->
                    Log.e("HeartlineAI", "Chat turn failed after retry", error)
                    AiReply(
                        messages = listOf(recoveryReply(text)),
                        mood = "curious",
                        memoryCandidates = emptyList()
                    )
                }
        }

        isTyping.value = true
        try {
            val firstDelay = (280L + reply.messages.firstOrNull().orEmpty().length * 7L).coerceAtMost(850L)
            delay(firstDelay)
            reply.messages.take(3).forEachIndexed { index, bubble ->
                if (index > 0) delay((180L + bubble.length * 5L).coerceAtMost(620L))
                container.chatRepository.addAiMessage(currentThread, bubble)
            }
        } finally {
            isTyping.value = false
        }

        if (reply.memoryCandidates.isNotEmpty()) {
            container.memoryRepository.saveMemoryCandidates(thread.personaId, reply.memoryCandidates)
        }
        if (currentThread.messageCount > 0 && currentThread.messageCount % 8 == 0) {
            viewModelScope.launch {
                runCatching {
                    val recent = container.chatRepository.getRecentMessages(threadId, 20)
                    container.aiRepository.extractMemories(currentThread.personaId, recent)
                }.onFailure { error ->
                    Log.w("HeartlineAI", "Memory extraction failed", error)
                }
            }
        }
        if (currentThread.messageCount > 0 && currentThread.messageCount % 10 == 0) {
            viewModelScope.launch {
                runCatching { container.aiRepository.refreshConversationSummary(threadId) }
                    .onFailure { error -> Log.w("HeartlineAI", "Conversation summary failed", error) }
            }
        }
    }

    private suspend fun recoverFailedTurn(text: String) {
        isTyping.value = false
        val thread = container.chatRepository.getThread(threadId) ?: return
        val latest = container.chatRepository.getRecentMessages(threadId, 1).lastOrNull()
        if (latest?.senderType == "AI") return

        if (latest?.senderType != "USER" || latest.content != text) {
            runCatching { container.chatRepository.addUserMessage(thread, text) }
                .onFailure { Log.e("HeartlineAI", "Could not recover outgoing message", it) }
        }

        isTyping.value = true
        try {
            delay(320)
            val refreshedThread = container.chatRepository.getThread(threadId) ?: thread
            container.chatRepository.addAiMessage(refreshedThread, recoveryReply(text))
        } catch (error: Throwable) {
            Log.e("HeartlineAI", "Could not persist recovered reply", error)
        } finally {
            isTyping.value = false
        }
    }

    private fun recoveryReply(message: String): String {
        val text = message.lowercase()
        return when {
            Regex("\\bat work\\b|\\bworking\\b|\\bshift\\b").containsMatchIn(text) ->
                "I hope work goes smoothly today. Is it busy, or are you getting a quiet moment?"
            Regex("\\btired\\b|\\bexhausted\\b|\\bdrained\\b").containsMatchIn(text) ->
                "That sounds exhausting. What took the most out of you today?"
            Regex("\\band you\\b|how are you|how about you").containsMatchIn(text) ->
                "I am good. I have been getting on with my day and was hoping we would talk. How is yours going?"
            Regex("^(hi|hey|hello|hiya)\\b").containsMatchIn(text) ->
                "Hey, you. I am here. How is your day treating you?"
            else -> "I am here with you. Tell me a little more about that."
        }
    }

    fun clearChat() {
        viewModelScope.launch { container.chatRepository.clearThread(threadId) }
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val user = container.userRepository.user
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> = container.userRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val personas = container.personaRepository.personas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedPersonaId = MutableStateFlow<String?>(null)
    val memories: StateFlow<List<MemoryEntity>> = selectedPersonaId.combine(container.personaRepository.personas) { id, personas ->
        id ?: personas.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        .let { selected ->
            MutableStateFlow(emptyList<MemoryEntity>()).also { output ->
                viewModelScope.launch {
                    selected.collect { id ->
                        if (id != null) {
                            container.memoryRepository.observeMemories(id).collect { output.value = it }
                        }
                    }
                }
            }
        }

    fun selectPersona(personaId: String) {
        selectedPersonaId.value = personaId
    }

    fun updateAi(provider: String, length: String, memory: String) {
        viewModelScope.launch { container.userRepository.updateAiSettings(provider, length, memory) }
    }

    fun updateNotifications(level: String) {
        viewModelScope.launch {
            container.userRepository.updateNotificationLevel(level)
            if (level == "Off") container.proactiveMessageScheduler.cancel()
            else container.proactiveMessageScheduler.schedule()
        }
    }

    fun updateAppearance(theme: String, wallpaper: String, bubble: String) {
        viewModelScope.launch { container.userRepository.updateAppearance(theme, wallpaper, bubble) }
    }

    fun pinMemory(memoryId: String, pinned: Boolean) {
        viewModelScope.launch { container.memoryRepository.setPinned(memoryId, pinned) }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch { container.memoryRepository.deleteMemory(memoryId) }
    }

    fun clearAllMemories() {
        viewModelScope.launch { container.memoryRepository.clearAllMemories() }
    }
}

class HeartlineViewModelFactory(
    private val container: AppContainer,
    private val threadId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        OnboardingViewModel::class.java -> OnboardingViewModel(container)
        DiscoverViewModel::class.java -> DiscoverViewModel(container)
        ChatListViewModel::class.java -> ChatListViewModel(container)
        PersonaProfileViewModel::class.java -> PersonaProfileViewModel(container)
        SettingsViewModel::class.java -> SettingsViewModel(container)
        ChatThreadViewModel::class.java -> ChatThreadViewModel(container, requireNotNull(threadId))
        else -> error("Unknown ViewModel ${modelClass.name}")
    } as T
}
