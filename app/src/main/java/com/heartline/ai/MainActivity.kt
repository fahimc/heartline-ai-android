package com.heartline.ai

import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.heartline.ai.navigation.HeartlineNav
import com.heartline.ai.navigation.Routes
import com.heartline.ai.ui.theme.HeartlineTheme

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
    LaunchedEffect(Unit) {
        app.container.personaRepository.seedIfNeeded()
        app.container.proactiveMessageScheduler.schedule()
    }
    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        HeartlineNav(
            startDestination = if (current.onboardingComplete) Routes.Discover else Routes.Onboarding,
            openThreadId = openThreadId
        )
    }
}
