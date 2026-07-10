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
import kotlinx.coroutines.delay
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
            container.proactiveMessageScheduler.schedule()
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
    }

    fun send() {
        val text = input.value.trim()
        if (text.isBlank()) return
        input.value = ""
        viewModelScope.launch {
            val thread = container.chatRepository.getThread(threadId) ?: return@launch
            container.chatRepository.addUserMessage(thread, text)
            isTyping.value = true
            try {
                delay(700)
                val currentThread = container.chatRepository.getThread(threadId) ?: thread
                val reply = container.aiRepository.generateReply(currentThread, text)
                for (bubble in reply.messages.take(4)) {
                    delay(350)
                    container.chatRepository.addAiMessage(currentThread, bubble)
                }
                if (reply.memoryCandidates.isNotEmpty()) {
                    container.memoryRepository.saveMemoryCandidates(thread.personaId, reply.memoryCandidates)
                }
                val recent = container.chatRepository.getRecentMessages(threadId, 10)
                if (recent.size >= 6) {
                    container.aiRepository.extractMemories(thread.personaId, recent)
                }
            } catch (error: Throwable) {
                Log.e("HeartlineAI", "Bundled LLM reply failed", error)
            } finally {
                isTyping.value = false
            }
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
