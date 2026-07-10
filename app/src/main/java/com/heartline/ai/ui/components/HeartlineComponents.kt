package com.heartline.ai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartline.ai.data.local.entities.PersonaProfileEntity
import com.heartline.ai.ui.theme.HeartlineBlack
import com.heartline.ai.ui.theme.HeartlineBlue
import com.heartline.ai.ui.theme.HeartlineGreen
import com.heartline.ai.ui.theme.HeartlineInk
import com.heartline.ai.ui.theme.HeartlineMuted
import com.heartline.ai.ui.theme.HeartlineOrange
import com.heartline.ai.ui.theme.HeartlinePanel
import com.heartline.ai.ui.theme.HeartlinePanelHigh
import com.heartline.ai.ui.theme.HeartlineRed
import com.heartline.ai.ui.theme.HeartlineStroke
import com.heartline.ai.ui.theme.HeartlineText
import com.heartline.ai.ui.theme.HeartlineViolet
import com.heartline.ai.util.initials
import com.heartline.ai.util.jsonListText

@Composable
fun HeartlineBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HeartlineBlack, HeartlineInk, Color(0xFF190906))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = 58.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = HeartlineStroke.copy(alpha = 0.34f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += grid
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = HeartlineStroke.copy(alpha = 0.24f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += grid
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HeartlineRed.copy(alpha = 0.42f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.95f),
                    radius = size.width * 0.9f
                ),
                radius = size.width,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
            )
        }
        content()
    }
}

@Composable
fun HeartlineCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.border(1.dp, HeartlineStroke.copy(alpha = 0.7f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HeartlinePanel.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val tile = Color.White.copy(alpha = 0.12f)
            for (index in 0..8) {
                val left = size.width * ((index * 17 % 86) / 100f)
                val top = size.height * ((index * 23 % 76) / 100f)
                drawRoundRect(
                    color = tile,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(34.dp.toPx(), 34.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }

            val face = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.38f)
            drawCircle(Color(0xFFFFC1A7).copy(alpha = 0.92f), radius = size.minDimension * 0.15f, center = face)
            drawCircle(
                color = Color(0xFF1B1210).copy(alpha = 0.88f),
                radius = size.minDimension * 0.18f,
                center = face.copy(y = face.y - size.minDimension * 0.03f),
                style = Stroke(width = size.minDimension * 0.08f)
            )
            val shoulders = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.84f)
                cubicTo(size.width * 0.3f, size.height * 0.58f, size.width * 0.7f, size.height * 0.58f, size.width * 0.78f, size.height * 0.84f)
                close()
            }
            drawPath(shoulders, Color(0xFF151518).copy(alpha = 0.92f))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.7f))))
        )
        Text(
            initials(persona.name),
            modifier = Modifier
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.24f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            "AI portrait",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall
        )
        if (showAiBadge) {
            Text(
                "AI",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(HeartlineRed)
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
        tags.take(3).forEach { tag -> HeartlinePill(tag) }
    }
}

@Composable
fun HeartlinePill(text: String, modifier: Modifier = Modifier, accent: Color = HeartlineOrange) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(HeartlinePanelHigh)
            .border(1.dp, accent.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        color = HeartlineText,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun HeartlineIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Composable
fun MainBottomBar(current: String, unreadCount: Int, onDiscover: () -> Unit, onChats: () -> Unit) {
    NavigationBar(containerColor = HeartlineBlack.copy(alpha = 0.98f), contentColor = Color.White) {
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
                    badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }
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
        containerColor = HeartlinePanel,
        titleContentColor = HeartlineText,
        textContentColor = HeartlineMuted,
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
        colors = CardDefaults.cardColors(containerColor = HeartlinePanel)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonaPortrait(persona, Modifier.fillMaxWidth().height(230.dp))
            Text("${persona.name}, ${persona.age}", style = MaterialTheme.typography.headlineSmall, color = HeartlineText, fontWeight = FontWeight.Bold)
            Text(persona.bio, style = MaterialTheme.typography.bodyMedium, color = HeartlineMuted)
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
        Text(title, style = MaterialTheme.typography.labelLarge, color = HeartlineOrange, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = HeartlineText.copy(alpha = 0.82f))
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
    "cream" -> listOf(Color(0xFFFFA047), HeartlineRed, HeartlinePanel)
    "coral" -> listOf(Color(0xFFFF321D), Color(0xFFFF7A18), HeartlinePanel)
    "plum" -> listOf(Color(0xFF8E163F), HeartlineRed, HeartlinePanel)
    "sunrise" -> listOf(HeartlineOrange, HeartlineRed, HeartlinePanel)
    "lavender" -> listOf(HeartlineViolet, Color(0xFFFF3864), HeartlinePanel)
    "teal" -> listOf(HeartlineBlue, HeartlineViolet, HeartlinePanel)
    "wine" -> listOf(Color(0xFF5C1023), HeartlineRed, HeartlinePanel)
    else -> listOf(HeartlineRed, HeartlineOrange, HeartlinePanel)
}
