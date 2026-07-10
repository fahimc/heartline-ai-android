package com.heartline.ai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.heartline.ai.ai.AssetLoadingState
import com.heartline.ai.navigation.HeartlineNav
import com.heartline.ai.navigation.Routes
import com.heartline.ai.ui.assetloading.AssetLoadingScreen
import com.heartline.ai.ui.splash.HeartlineSplashScreen
import com.heartline.ai.ui.theme.HeartlineTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openThreadId = intent?.getStringExtra("threadId")
        setContent {
            HeartlineTheme {
                HeartlineRoot(openThreadId)
            }
        }
    }
}

@Composable
private fun HeartlineRoot(openThreadId: String?) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.container.userRepository.settings.collectAsState(initial = null)
    val assetLoadingState by app.container.modelAssetManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var splashComplete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_200)
        splashComplete = true
    }
    LaunchedEffect(Unit) {
        app.container.personaRepository.seedIfNeeded()
        app.container.proactiveMessageScheduler.schedule()
    }
    LaunchedEffect(assetLoadingState) {
        if (assetLoadingState is AssetLoadingState.Ready) {
            runCatching { app.container.preloadAi() }
                .onFailure { Log.w("HeartlineAI", "Qwen preload failed", it) }
        }
    }
    val current = settings
    if (!splashComplete) {
        HeartlineSplashScreen()
    } else if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (assetLoadingState !is AssetLoadingState.Ready) {
        AssetLoadingScreen(
            state = assetLoadingState,
            onLoadAsset = {
                scope.launch {
                    app.container.modelAssetManager.loadAsset()
                }
            }
        )
    } else {
        HeartlineNav(
            startDestination = if (current.onboardingComplete) Routes.Discover else Routes.Onboarding,
            openThreadId = openThreadId
        )
    }
}
