package com.heartline.ai

import android.content.Intent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.heartline.ai.navigation.HeartlineNav
import com.heartline.ai.navigation.Routes
import com.heartline.ai.ui.splash.HeartlineSplashScreen
import com.heartline.ai.ui.theme.HeartlineTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openThreadRequest = MutableStateFlow<OpenThreadRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptNotificationIntent(intent)
        setContent {
            HeartlineTheme {
                val request by openThreadRequest.collectAsState()
                HeartlineRoot(request)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNotificationIntent(intent)
    }

    private fun acceptNotificationIntent(intent: Intent?) {
        intent?.getStringExtra("threadId")?.takeIf(String::isNotBlank)?.let { threadId ->
            openThreadRequest.value = OpenThreadRequest(threadId, System.nanoTime())
        }
    }
}

private data class OpenThreadRequest(val threadId: String, val eventId: Long)

@Composable
private fun HeartlineRoot(openThreadRequest: OpenThreadRequest?) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.container.userRepository.settings.collectAsState(initial = null)
    var splashComplete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_200)
        splashComplete = true
    }
    LaunchedEffect(Unit) {
        app.container.personaRepository.seedIfNeeded()
        if (app.container.userRepository.getUser()?.notificationLevel == "Off") {
            app.container.proactiveMessageScheduler.cancel()
        } else {
            app.container.proactiveMessageScheduler.schedule()
        }
    }
    LaunchedEffect(Unit) {
        delay(2_000)
        runCatching { app.container.prepareAi() }
            .onFailure { Log.w("HeartlineAI", "Silent Qwen3 preparation failed; chat will retry", it) }
    }
    val current = settings
    if (!splashComplete) {
        HeartlineSplashScreen()
    } else if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        HeartlineNav(
            startDestination = if (current.onboardingComplete) Routes.Discover else Routes.Onboarding,
            openThreadId = openThreadRequest?.threadId,
            openThreadEventId = openThreadRequest?.eventId
        )
    }
}
