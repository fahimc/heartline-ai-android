package com.heartline.ai

import android.content.Context
import com.heartline.ai.ai.BundledLlmModelProvider
import com.heartline.ai.ai.MemoryExtractor
import com.heartline.ai.ai.MemoryRetriever
import com.heartline.ai.ai.RelationshipEngine
import com.heartline.ai.data.local.AppDatabase
import com.heartline.ai.data.repository.AiRepository
import com.heartline.ai.data.repository.ChatRepository
import com.heartline.ai.data.repository.MemoryRepository
import com.heartline.ai.data.repository.NotificationRepository
import com.heartline.ai.data.repository.PersonaRepository
import com.heartline.ai.data.repository.UserPreferencesDataStore
import com.heartline.ai.data.repository.UserRepository
import com.heartline.ai.notifications.NotificationHelper
import com.heartline.ai.notifications.ProactiveMessageScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val aiModelProvider = BundledLlmModelProvider(appContext)
    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = UserPreferencesDataStore(appContext)
    val userRepository = UserRepository(database.userDao(), settingsStore)
    val personaRepository = PersonaRepository(database.personaDao(), database.moodDao())
    val chatRepository = ChatRepository(database.chatDao(), database.personaDao(), RelationshipEngine())
    val memoryRepository = MemoryRepository(
        database.memoryDao(),
        database.summaryDao(),
        MemoryRetriever(),
        MemoryExtractor()
    )
    val aiRepository = AiRepository(
        aiModelProvider,
        userRepository,
        personaRepository,
        chatRepository,
        memoryRepository
    )
    val notificationHelper = NotificationHelper(appContext)
    val proactiveMessageScheduler = ProactiveMessageScheduler(appContext)
    val notificationRepository = NotificationRepository(proactiveMessageScheduler, notificationHelper)

    suspend fun preloadAi() {
        aiModelProvider.preload()
    }
}
