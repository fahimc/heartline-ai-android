package com.heartline.ai.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlin.random.Random
import java.util.concurrent.TimeUnit

class ProactiveMessageScheduler(private val context: Context) {
    fun schedule() {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        val request = PeriodicWorkRequestBuilder<ProactiveMessageWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setInitialDelay(Random.nextLong(15, 21), TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        scheduleCheckSoon()
    }

    fun scheduleAfterConnection(threadId: String) {
        val request = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
            .setInitialDelay(Random.nextLong(2, 6), TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    ProactiveMessageWorker.TARGET_THREAD_ID_KEY to threadId,
                    ProactiveMessageWorker.NEW_CONNECTION_KEY to true
                )
            )
            .addTag(CONNECTION_WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$CONNECTION_WORK_PREFIX$threadId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(WORK_NAME)
            cancelUniqueWork(LEGACY_WORK_NAME)
            cancelUniqueWork(BOOTSTRAP_WORK_NAME)
            cancelAllWorkByTag(CONNECTION_WORK_TAG)
        }
    }

    private fun scheduleCheckSoon() {
        val request = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
            .setInitialDelay(Random.nextLong(2, 6), TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOTSTRAP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_NAME = "heartline_proactive_messages_v2"
        const val LEGACY_WORK_NAME = "heartline_proactive_messages"
        const val BOOTSTRAP_WORK_NAME = "heartline_proactive_bootstrap_v2"
        const val CONNECTION_WORK_PREFIX = "heartline_connection_message_"
        const val CONNECTION_WORK_TAG = "heartline_connection_messages"
    }
}
