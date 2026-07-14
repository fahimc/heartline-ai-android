package com.heartline.ai.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartline.ai.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val personas by viewModel.personas.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Section("Profile") {
                Text("Display name: ${user?.displayName ?: "Friend"}")
                Text("Preferred tone: ${user?.preferredTone ?: "Supportive"}")
            }
            Section("Notifications") {
                Text("Spontaneous messages")
                ChipRow(listOf("Off", "Light", "Normal", "Frequent"), user?.notificationLevel ?: "Normal") {
                    if (it != "Off" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.updateNotifications(it)
                }
                Text("Quiet hours: ${user?.quietHoursStart ?: "22:00"} to ${user?.quietHoursEnd ?: "07:00"}")
            }
            Section("Privacy") {
                Text("Memories are stored locally on this device.")
                Text("View, pin, delete, or clear stored memories.")
                if (personas.isNotEmpty()) {
                    ChipRow(personas.map { it.name }, personas.first().name) { selectedName ->
                        personas.firstOrNull { it.name == selectedName }?.let { viewModel.selectPersona(it.id) }
                    }
                }
                memories.take(6).forEach { memory ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                            Text(memory.type, style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.Switch(checked = memory.isPinned, onCheckedChange = { viewModel.pinMemory(memory.id, it) })
                    }
                    Divider()
                }
                OutlinedButton(onClick = viewModel::clearAllMemories) { Text("Clear all memories") }
            }
            Section("AI Engine") {
                Text("Qwen3 runs privately on this device. Heartline grounds each reply in the current chat, persona, and local memories before generation.")
                Text("Response length")
                ChipRow(listOf("Short", "Normal", "Detailed"), settings.responseLength) {
                    viewModel.updateAi("Bundled Qwen3", it, settings.memoryRetrieval)
                }
                Text("Memory retrieval")
                ChipRow(listOf("Basic", "Strong", "Off"), settings.memoryRetrieval) {
                    viewModel.updateAi("Bundled Qwen3", settings.responseLength, it)
                }
            }
            Section("Appearance") {
                Text("Theme")
                ChipRow(listOf("System", "Light", "Dark"), settings.theme) {
                    viewModel.updateAppearance(it, settings.chatWallpaper, settings.bubbleStyle)
                }
                Text("Chat wallpaper")
                ChipRow(listOf("Warm", "Clean", "Night"), settings.chatWallpaper) {
                    viewModel.updateAppearance(settings.theme, it, settings.bubbleStyle)
                }
                Text("Bubble style")
                ChipRow(listOf("Soft", "Compact", "Rounded"), settings.bubbleStyle) {
                    viewModel.updateAppearance(settings.theme, settings.chatWallpaper, it)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.take(4).forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, label = { Text(option) })
        }
    }
}
