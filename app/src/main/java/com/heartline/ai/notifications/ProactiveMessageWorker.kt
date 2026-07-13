package com.heartline.ai.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heartline.ai.HeartlineApplication
import com.heartline.ai.data.local.entities.ChatThreadEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ProactiveMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? HeartlineApplication ?: return Result.failure()
        val container = app.container
        val user = container.userRepository.getUser() ?: return Result.success()
        if (user.notificationLevel == "Off" || QuietHoursManager().isQuietNow(user)) {
            Log.d(TAG, "Skipping proactive check because notifications are off or quiet hours are active")
            return Result.success()
        }
        val forcedForDebug = inputData.getBoolean(FORCE_FOR_DEBUG_KEY, false)
        val targetThreadId = inputData.getString(TARGET_THREAD_ID_KEY)
        val newConnectionCheck = inputData.getBoolean(NEW_CONNECTION_KEY, false)

        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val limits = ProactiveLimits.forLevel(user.notificationLevel)
        val chatDao = container.database.chatDao()
        val totalToday = chatDao.countProactiveMessagesSince(startOfDay)
        if (!forcedForDebug && totalToday >= limits.maxTotalPerDay) {
            Log.d(TAG, "Skipping proactive check because the daily limit is reached")
            return Result.success()
        }
        val latestProactive = chatDao.latestProactiveMessageAt() ?: 0L
        if (!forcedForDebug && latestProactive > 0 && now - latestProactive < limits.minimumGlobalGapMillis) {
            Log.d(TAG, "Skipping proactive check because the global gap is active")
            return Result.success()
        }

        val candidates = container.chatRepository.getThreads().let { threads ->
            targetThreadId?.let { target -> threads.filter { it.id == target } } ?: threads
        }
        val eligible = candidates.filter { thread ->
            val idleLongEnough = now - thread.updatedAt >= limits.minimumIdleMillis
            val connectionReady = newConnectionCheck && thread.messageCount == 0
            (forcedForDebug || connectionReady || idleLongEnough) &&
                (forcedForDebug ||
                    chatDao.countProactiveMessagesForThreadSince(thread.id, startOfDay) < limits.maxPerPersonaPerDay)
        }
        if (eligible.isEmpty()) {
            Log.d(TAG, "No connected persona is eligible for a proactive message yet")
            return Result.success()
        }

        val guaranteedByIdle = eligible.any { now - it.updatedAt >= limits.maximumIdleBeforeGuaranteedMillis }
        if (!forcedForDebug && !newConnectionCheck && !guaranteedByIdle && Random.nextDouble() > limits.chancePerCheck) {
            Log.d(TAG, "Randomized proactive check deferred until a later window")
            return Result.success()
        }
        val selected = targetThreadId?.let { eligible.firstOrNull() } ?: chooseThread(eligible, now)
            ?: return Result.success()

        return try {
            val persona = container.personaRepository.getPersona(selected.personaId) ?: return Result.success()
            val reply = container.aiRepository.generateProactiveReply(selected)
            val message = reply.messages.firstOrNull()?.trim().orEmpty()
            if (message.isBlank()) return Result.retry()
            container.chatRepository.addAiMessage(selected, message, proactive = true)
            container.notificationHelper.showMessage(selected.id, persona.name, message)
            Log.i(TAG, "Delivered proactive message for thread ${selected.id}")
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "Proactive message delivery failed", error)
            Result.retry()
        }
    }

    private fun chooseThread(threads: List<ChatThreadEntity>, now: Long): ChatThreadEntity? {
        if (threads.isEmpty()) return null
        val weighted = threads.map { thread ->
            val idleHours = TimeUnit.MILLISECONDS.toHours(now - thread.updatedAt).coerceAtMost(24)
            val weight = 1 + thread.messageCount.coerceAtMost(30) + thread.affinityScore / 5 + idleHours.toInt()
            thread to weight.coerceAtLeast(1)
        }
        val total = weighted.sumOf { it.second }
        var ticket = Random.nextInt(total)
        weighted.forEach { (thread, weight) ->
            if (ticket < weight) return thread
            ticket -= weight
        }
        return weighted.last().first
    }

    companion object {
        const val TAG = "HeartlineAI"
        const val FORCE_FOR_DEBUG_KEY = "force_for_debug"
        const val TARGET_THREAD_ID_KEY = "target_thread_id"
        const val NEW_CONNECTION_KEY = "new_connection"
    }
}

data class ProactiveLimits(
    val maxTotalPerDay: Int,
    val maxPerPersonaPerDay: Int,
    val minimumGlobalGapMillis: Long,
    val minimumIdleMillis: Long,
    val maximumIdleBeforeGuaranteedMillis: Long,
    val chancePerCheck: Double
) {
    companion object {
        fun forLevel(level: String): ProactiveLimits = when (level) {
            "Light" -> ProactiveLimits(
                maxTotalPerDay = 1,
                maxPerPersonaPerDay = 1,
                minimumGlobalGapMillis = TimeUnit.HOURS.toMillis(6),
                minimumIdleMillis = TimeUnit.HOURS.toMillis(2),
                maximumIdleBeforeGuaranteedMillis = TimeUnit.HOURS.toMillis(10),
                chancePerCheck = 0.28
            )
            "Frequent" -> ProactiveLimits(
                maxTotalPerDay = 5,
                maxPerPersonaPerDay = 2,
                minimumGlobalGapMillis = TimeUnit.MINUTES.toMillis(45),
                minimumIdleMillis = TimeUnit.MINUTES.toMillis(20),
                maximumIdleBeforeGuaranteedMillis = TimeUnit.MINUTES.toMillis(75),
                chancePerCheck = 0.78
            )
            else -> ProactiveLimits(
                maxTotalPerDay = 3,
                maxPerPersonaPerDay = 1,
                minimumGlobalGapMillis = TimeUnit.HOURS.toMillis(2),
                minimumIdleMillis = TimeUnit.MINUTES.toMillis(45),
                maximumIdleBeforeGuaranteedMillis = TimeUnit.HOURS.toMillis(3),
                chancePerCheck = 0.55
            )
        }
    }
}
