package com.heartline.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.ui.theme.Blush
import com.heartline.ai.ui.theme.Cream
import com.heartline.ai.ui.theme.Gold
import com.heartline.ai.ui.theme.Ink
import com.heartline.ai.ui.theme.Mint
import com.heartline.ai.ui.theme.Rose
import com.heartline.ai.ui.theme.Wine
import com.heartline.ai.util.initials
import com.heartline.ai.util.jsonListText

@Composable
fun HeartlineBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B1725), Color(0xFF6D3048), Color(0xFFFFC1CF))
                )
            )
    ) {
        content()
    }
}

@Composable
fun PersonaPortrait(
    persona: PersonaProfileEntity,
    modifier: Modifier = Modifier,
    showAiBadge: Boolean = true
) {
    val colors = palette(persona.avatarUri)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colors))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        radius = 650f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials(persona.name),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Generated companion portrait",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (showAiBadge) {
            Text(
                "AI",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TagRow(tags: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.take(3).forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
fun MainBottomBar(current: String, unreadCount: Int, onDiscover: () -> Unit, onChats: () -> Unit) {
    NavigationBar(containerColor = Color(0xFF21151F), contentColor = Color.White) {
        NavigationBarItem(
            selected = current == "discover",
            onClick = onDiscover,
            icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
            label = { Text("Discover") }
        )
        NavigationBarItem(
            selected = current == "chats",
            onClick = onChats,
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
                    }
                ) { Icon(Icons.Default.ChatBubble, contentDescription = "Chats") }
            },
            label = { Text("Chats") }
        )
    }
}

@Composable
fun FloatingChatsButton(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = if (unread > 0) pulse else 1f
            scaleY = if (unread > 0) pulse else 1f
        }
    ) {
        Icon(Icons.Default.ChatBubble, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (unread > 0) "Chats $unread" else "Chats")
    }
}

@Composable
fun ConnectionDialog(personaName: String, onStartChat: () -> Unit, onKeepBrowsing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepBrowsing,
        title = { Text("Connection made") },
        text = { Text("$personaName is ready to chat when you are.") },
        confirmButton = { Button(onClick = onStartChat) { Text("Start chatting") } },
        dismissButton = { TextButton(onClick = onKeepBrowsing) { Text("Keep browsing") } }
    )
}

@Composable
fun FullProfileCard(
    persona: PersonaProfileEntity,
    onConnect: () -> Unit,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonaPortrait(persona, Modifier.fillMaxWidth().height(230.dp))
            Text("${persona.name}, ${persona.age}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(persona.bio, style = MaterialTheme.typography.bodyMedium)
            ProfileSection("Personality", persona.personalityJson.jsonListText().joinToString(", "))
            ProfileSection("Interests", persona.interestsJson.jsonListText().joinToString(", "))
            ProfileSection("Chat style", persona.chatStyle)
            ProfileSection("What she remembers well", persona.memoryPrioritiesJson.jsonListText().joinToString(", "))
            ProfileSection("Relationship style", persona.relationshipPace)
            ProfileSection("Sample opening", persona.proactiveMessageStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                TextButton(onClick = onPass, modifier = Modifier.weight(1f)) { Text("Pass") }
                Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Wine, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.82f))
    }
}

@Composable
fun EmptyComingSoon(onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More companions coming soon", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text("Refresh the deck or check your chats.", color = Color.White.copy(alpha = 0.8f))
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}

private fun palette(uri: String): List<Color> = when (uri.substringAfter(":")) {
    "cream" -> listOf(Cream, Gold, Rose)
    "coral" -> listOf(Color(0xFFFF8A65), Color(0xFFFFB199), Wine)
    "plum" -> listOf(Color(0xFF6E3A5A), Rose, Blush)
    "sunrise" -> listOf(Gold, Color(0xFFFF7A7A), Color(0xFF61304E))
    "lavender" -> listOf(Color(0xFFB99CFF), Blush, Color(0xFF4B3764))
    "teal" -> listOf(Mint, Color(0xFF5E6AD2), Ink)
    "wine" -> listOf(Wine, Color(0xFFC75772), Gold)
    else -> listOf(Rose, Blush, Wine)
}
