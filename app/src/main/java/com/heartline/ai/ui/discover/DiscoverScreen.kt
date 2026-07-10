package com.heartline.ai.ui.discover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.ui.DiscoverViewModel
import com.heartline.ai.ui.components.ConnectionDialog
import com.heartline.ai.ui.components.EmptyComingSoon
import com.heartline.ai.ui.components.FloatingChatsButton
import com.heartline.ai.ui.components.FullProfileCard
import com.heartline.ai.ui.components.HeartlineBackground
import com.heartline.ai.ui.components.MainBottomBar
import com.heartline.ai.ui.components.PersonaPortrait
import com.heartline.ai.ui.components.TagRow
import com.heartline.ai.util.jsonListText
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onChats: () -> Unit,
    onSettings: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = { Text("Heartline AI", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            MainBottomBar("discover", state.unreadCount, onDiscover = {}, onChats = onChats)
        }
    ) { padding ->
        HeartlineBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            ) {
                val persona = state.currentPersona
                if (persona == null) {
                    EmptyComingSoon(onRefresh = { viewModel.rewind() })
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SwipeCard(
                            persona = persona,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onPass = viewModel::pass,
                            onConnect = { viewModel.connect(persona) },
                            onProfile = { viewModel.viewProfile(persona) }
                        )
                        ActionRow(
                            onRewind = viewModel::rewind,
                            onPass = viewModel::pass,
                            onProfile = { viewModel.viewProfile(persona) },
                            onConnect = { viewModel.connect(persona) },
                            onSuper = { viewModel.connect(persona) }
                        )
                        Spacer(Modifier.height(72.dp))
                    }
                }
                FloatingChatsButton(
                    unread = state.unreadCount,
                    onClick = onChats,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 88.dp)
                )
            }
        }
    }

    state.connectedThread?.let { thread ->
        val personaName = state.personas.firstOrNull { it.id == thread.personaId }?.name ?: "Your companion"
        ConnectionDialog(
            personaName = personaName,
            onStartChat = {
                viewModel.dismissConnection()
                onOpenThread(thread.id)
            },
            onKeepBrowsing = viewModel::dismissConnection
        )
    }

    state.selectedProfile?.let { persona ->
        ModalBottomSheet(onDismissRequest = viewModel::closeProfile) {
            FullProfileCard(
                persona = persona,
                onConnect = {
                    viewModel.closeProfile()
                    viewModel.connect(persona)
                },
                onPass = {
                    viewModel.closeProfile()
                    viewModel.pass()
                },
                onBack = viewModel::closeProfile
            )
        }
    }
}

@Composable
private fun SwipeCard(
    persona: PersonaProfileEntity,
    modifier: Modifier,
    onPass: () -> Unit,
    onConnect: () -> Unit,
    onProfile: () -> Unit
) {
    var offsetX by remember(persona.id) { mutableFloatStateOf(0f) }
    val animatedX by animateFloatAsState(offsetX, label = "card-x")
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .graphicsLayer {
                translationX = animatedX
                rotationZ = animatedX / 42f
                alpha = 1f - (kotlin.math.abs(animatedX) / 900f).coerceIn(0f, 0.45f)
                scaleX = 1f - (kotlin.math.abs(animatedX) / 3000f).coerceIn(0f, 0.04f)
                scaleY = scaleX
            }
            .pointerInput(persona.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    },
                    onDragEnd = {
                        when {
                            offsetX > 170f -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConnect()
                            }
                            offsetX < -170f -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPass()
                            }
                        }
                        offsetX = 0f
                    }
                )
            },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            PersonaPortrait(persona, Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.42f)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${persona.name}, ${persona.age}",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onProfile) {
                            Icon(Icons.Default.Info, contentDescription = "View profile", tint = Color.White)
                        }
                    }
                    Text(persona.tagline, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Nearby in imagination", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
                    TagRow(persona.personalityJson.jsonListText())
                    Text("Compatibility hint: ${persona.chatStyle.take(58)}", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onRewind: () -> Unit,
    onPass: () -> Unit,
    onProfile: () -> Unit,
    onConnect: () -> Unit,
    onSuper: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundAction(Icons.Default.Undo, "Rewind", onRewind, Color(0xFFFFD08A))
        RoundAction(Icons.Default.Close, "Pass", onPass, Color(0xFFFF8A8A), 60)
        RoundAction(Icons.Default.Info, "Profile", onProfile, Color.White)
        RoundAction(Icons.Default.Favorite, "Connect", onConnect, Color(0xFFFF6D8E), 60)
        RoundAction(Icons.Default.Star, "Super connect", onSuper, Color(0xFFA9E6D2))
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, color: Color, size: Int = 52) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Icon(icon, contentDescription = label, tint = color)
    }
}
