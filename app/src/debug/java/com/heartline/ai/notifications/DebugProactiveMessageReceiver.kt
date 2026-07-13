package com.heartline.ai.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.heartline.ai.HeartlineApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugProactiveMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as HeartlineApplication
                app.container.personaRepository.seedIfNeeded()
                if (app.container.userRepository.getUser() == null) {
                    app.container.userRepository.saveProfile(
                        displayName = "Test User",
                        preferredTone = "Sweet",
                        notificationLevel = "Frequent",
                        quietStart = "23:59",
                        quietEnd = "00:00"
                    )
                }
                app.container.chatRepository.connectPersona("maya")
                val request = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                    .setInputData(workDataOf(ProactiveMessageWorker.FORCE_FOR_DEBUG_KEY to true))
                    .addTag("heartline_debug_proactive")
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.heartline.ai.DEBUG_PROACTIVE"
    }
}
