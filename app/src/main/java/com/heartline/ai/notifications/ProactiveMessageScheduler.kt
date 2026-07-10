package com.heartline.ai.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ProactiveMessageScheduler(private val context: Context) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ProactiveMessageWorker>(3, TimeUnit.HOURS)
            .setInitialDelay(45, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val WORK_NAME = "heartline_proactive_messages"
    }
}
