package com.heartline.ai.ui.assetloading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartline.ai.ai.AssetLoadingState

@Composable
fun AssetLoadingScreen(
    state: AssetLoadingState,
    onLoadAsset: () -> Unit
) {
    val downloading = state as? AssetLoadingState.Downloading
    val failed = state as? AssetLoadingState.Failed
    val isBusy = state is AssetLoadingState.Checking || downloading != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050505), Color(0xFF1B1010), Color(0xFF050505))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2028),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFF2D20))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state is AssetLoadingState.Ready) Icons.Default.Verified else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Asset Loading",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Heartline is preparing the app assets needed for private on-device chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC7C7D1)
                    )
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Package", color = Color(0xFF8E929F))
                        Text("Companion assets", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Size", color = Color(0xFF8E929F))
                        Text("2.41 GB", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Storage", color = Color(0xFF8E929F))
                        Text("Local only", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (downloading != null) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { downloading.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp)),
                            color = Color(0xFFFF2D20),
                            trackColor = Color(0xFF343743)
                        )
                        Text(
                            text = "${(downloading.progress * 100).toInt()}% loaded",
                            color = Color(0xFFC7C7D1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (failed != null) {
                    Text(
                        text = failed.message,
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onLoadAsset,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D20))
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = when {
                            downloading != null -> "Loading assets"
                            failed != null -> "Retry asset loading"
                            else -> "Start asset loading"
                        }
                    )
                }

                Text(
                    text = "Use Wi-Fi. Heartline will verify everything before opening chat.",
                    color = Color(0xFF8E929F),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
