package com.heartline.ai

import android.app.Application
import android.util.Log
import com.heartline.ai.ai.AssetLoadingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HeartlineApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()
        appScope.launch {
            if (container.modelAssetManager.state.value is AssetLoadingState.Ready) {
                runCatching { container.preloadAi() }
                    .onFailure { Log.w("HeartlineAI", "Qwen preload failed", it) }
            }
        }
    }
}
