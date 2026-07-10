package com.heartline.ai.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heartline.ai.ai.BundledLlmModelProvider
import com.heartline.ai.ai.RoutineEngine
import com.heartline.ai.data.local.AppDatabase
import com.heartline.ai.domain.model.ProactiveMessageRequest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProactiveMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val user = db.userDao().getUser() ?: return Result.success()
        if (user.notificationLevel == "Off" || QuietHoursManager().isQuietNow(user)) return Result.success()

        val maxTotal = when (user.notificationLevel) {
            "Light" -> 1
            "Frequent" -> 5
            else -> 3
        }
        val threads = db.chatDao().observeThreads().first()
            .filter { System.currentTimeMillis() - it.updatedAt > TimeUnit.HOURS.toMillis(2) }
            .sortedByDescending { it.messageCount }
            .take(maxTotal)
        val provider = BundledLlmModelProvider(applicationContext)
        val routine = RoutineEngine()
        val notifier = NotificationHelper(applicationContext)

        threads.forEach { thread ->
            val persona = db.personaDao().getPersona(thread.personaId) ?: return@forEach
            val mood = db.moodDao().getMood(persona.id)
            val memories = db.memoryDao().getPinned(persona.id).take(3)
            val raw = provider.generateProactiveMessage(
                ProactiveMessageRequest(
                    persona = persona,
                    user = user,
                    thread = thread,
                    mood = mood,
                    memories = memories,
                    timeOfDay = routine.timeOfDay(),
                    lastInteraction = "recently"
                )
            )
            val message = raw.extractProactiveMessage() ?: return@forEach
            val saved = com.heartline.ai.data.local.entities.MessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                threadId = thread.id,
                senderType = "AI",
                content = message,
                source = "PROACTIVE"
            )
            db.chatDao().insertMessage(saved)
            db.chatDao().updateThread(
                thread.copy(
                    lastMessage = message,
                    unreadCount = thread.unreadCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
            notifier.showMessage(thread.id, persona.name, message)
        }
        return Result.success()
    }

    private fun String.extractProactiveMessage(): String? {
        val cleaned = cleanupModelText()
        val json = runCatching {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start == -1 || end <= start) null else JSONObject(cleaned.substring(start, end + 1))
        }.getOrNull()
        return (json?.optString("message") ?: cleaned).cleanupModelText().takeIf { it.isNotBlank() }
    }

    private fun String.cleanupModelText(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
