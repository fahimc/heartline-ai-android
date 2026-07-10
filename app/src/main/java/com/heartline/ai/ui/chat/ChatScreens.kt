package com.heartline.ai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.ChatRow
import com.heartline.ai.ui.ChatListViewModel
import com.heartline.ai.ui.ChatThreadViewModel
import com.heartline.ai.ui.components.MainBottomBar
import com.heartline.ai.ui.components.PersonaPortrait
import com.heartline.ai.util.chatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onDiscover: () -> Unit,
    onSettings: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val rows by viewModel.chatRows.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heartline AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onDiscover) { Icon(Icons.Default.Explore, contentDescription = "Discover") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFF7F1))
            )
        },
        bottomBar = {
            MainBottomBar("chats", rows.sumOf { it.thread.unreadCount }, onDiscover = onDiscover, onChats = {})
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF7F1))
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search chats") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No chats yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Discover companions and connect when someone feels right.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onDiscover) { Text("Discover companions") }
                }
            } else {
                LazyColumn {
                    items(rows, key = { it.thread.id }) { row ->
                        ChatRowItem(row, onClick = { onOpenThread(row.thread.id) })
                        Divider(color = Color(0xFFFFE1EA))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRowItem(row: ChatRow, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = {}),
        headlineContent = { Text(row.persona.name, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Text(
                row.thread.lastMessage.ifBlank { "Connection made. Start the conversation." },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { PersonaPortrait(row.persona, Modifier.size(56.dp).clip(CircleShape), showAiBadge = false) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.thread.updatedAt.chatTime(), style = MaterialTheme.typography.labelSmall)
                if (row.thread.unreadCount > 0) Badge { Text(row.thread.unreadCount.toString()) }
                Text(if (row.thread.unreadCount > 0) "typing..." else "online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B8F6A))
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    onBack: () -> Unit,
    onDiscover: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.input.collectAsState()
    val persona = state.persona
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (persona != null) PersonaPortrait(persona, Modifier.size(40.dp).clip(CircleShape), showAiBadge = false)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(persona?.name ?: "Chat", fontWeight = FontWeight.Bold)
                            Text(if (state.isTyping) "typing..." else "online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF237A61))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onDiscover) { Icon(Icons.Default.Explore, contentDescription = "Discover") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Person, contentDescription = "Persona profile") }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFF7F1))
            )
        },
        bottomBar = {
            MessageInput(
                value = input,
                onValueChange = { viewModel.input.value = it },
                onSend = viewModel::send,
                modifier = Modifier.imePadding()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF7F1))
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            item {
                DateSeparator("Today")
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (state.isTyping) {
                item { TypingDots() }
            }
        }
    }
}

@Composable
private fun MessageInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier) {
    Surface(color = Color(0xFFFFF7F1), shadowElevation = 8.dp, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) { Text(":)") }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            IconButton(onClick = {}) { Icon(Icons.Default.Mic, contentDescription = "Voice placeholder") }
            IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFFF6D8E)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.senderType == "USER"
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isUser) Color(0xFFFFD3DF) else Color.White)
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(12.dp)
        ) {
            Text(message.content)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    if (isUser) "${message.createdAt.chatTime()}  sent" else message.createdAt.chatTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Message") },
            text = { Text(message.content) },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    menu = false
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { menu = false }) { Text("Delete") }
            }
        )
    }
}

@Composable
private fun DateSeparator(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFE2EA))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun TypingDots() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Text("typing...", Modifier.padding(14.dp), color = Color.Gray)
        }
    }
}
