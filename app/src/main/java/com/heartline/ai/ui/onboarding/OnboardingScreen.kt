package com.heartline.ai.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartline.ai.ui.OnboardingViewModel
import com.heartline.ai.ui.components.HeartlineBackground
import com.heartline.ai.ui.theme.HeartlineMuted
import com.heartline.ai.ui.theme.HeartlinePanel
import com.heartline.ai.ui.theme.HeartlinePanelHigh
import com.heartline.ai.ui.theme.HeartlineRed
import com.heartline.ai.ui.theme.HeartlineStroke
import com.heartline.ai.ui.theme.HeartlineText

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val name by viewModel.userName.collectAsState()
    val tone by viewModel.preferredTone.collectAsState()
    val notifications by viewModel.notificationLevel.collectAsState()
    val quietStart by viewModel.quietStart.collectAsState()
    val quietEnd by viewModel.quietEnd.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    HeartlineBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text("Heartline AI", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Meet AI companions with real personalities. Choose who you connect with, chat naturally, and let them remember what matters.",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.titleMedium
            )

            Surface(
                color = HeartlinePanel.copy(alpha = 0.94f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingBullet("Choose fictional adult AI companions")
                    OnboardingBullet("Chat in short, natural mobile messages")
                    OnboardingBullet("Store useful memories locally on this device")
                    OnboardingBullet("Enable spontaneous messages when you want them")

                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.userName.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display name") },
                        singleLine = true,
                        colors = inputColors()
                    )

                    Text("Preferred chat tone", color = HeartlineText, fontWeight = FontWeight.Bold)
                    ChipRow(listOf("Sweet", "Playful", "Flirty", "Calm", "Supportive"), tone) {
                        viewModel.preferredTone.value = it
                    }

                    Text("Notifications", color = HeartlineText, fontWeight = FontWeight.Bold)
                    ChipRow(listOf("Off", "Light", "Normal", "Frequent"), notifications) {
                        viewModel.notificationLevel.value = it
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quietStart,
                            onValueChange = { viewModel.quietStart.value = it },
                            label = { Text("Quiet start") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = inputColors()
                        )
                        OutlinedTextField(
                            value = quietEnd,
                            onValueChange = { viewModel.quietEnd.value = it },
                            label = { Text("Quiet end") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = inputColors()
                        )
                    }

                    Button(
                        onClick = {
                            if (notifications != "Off" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.finish()
                            onFinished()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start discovering")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingBullet(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(HeartlinePanelHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
        color = HeartlineText,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = HeartlinePanelHigh,
                    labelColor = HeartlineText,
                    selectedContainerColor = HeartlineRed,
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = HeartlineStroke,
                    selectedBorderColor = HeartlineRed,
                    enabled = true,
                    selected = selected == option
                )
            )
        }
    }
}

@Composable
private fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HeartlineText,
    unfocusedTextColor = HeartlineText,
    focusedBorderColor = HeartlineRed,
    unfocusedBorderColor = HeartlineStroke,
    focusedContainerColor = HeartlinePanelHigh,
    unfocusedContainerColor = HeartlinePanelHigh,
    focusedLabelColor = HeartlineRed,
    unfocusedLabelColor = HeartlineMuted
)
