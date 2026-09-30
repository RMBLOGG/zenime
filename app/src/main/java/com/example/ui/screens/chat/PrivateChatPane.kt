package com.example.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.FriendDisplay
import com.example.data.model.PrivateMessage
import com.example.ui.components.GeneratedAvatar
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dmTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatDmTime(iso: String): String = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(dmTimeFormatter)
} catch (e: Exception) {
    ""
}

/**
 * Isi tab "Teman": daftar percakapan (kalau belum milih chat) atau ruang
 * chat 1-lawan-1. Input bar selalu tampil di bawah -- nonaktif dengan
 * placeholder "Pilih chat untuk lanjut percakapan" sampai user milih teman.
 */
@Composable
fun ColumnScope.PrivateChatPane(
    viewModel: PrivateChatViewModel,
    myUid: String,
    onFriendProfileClick: (uid: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.ensureStarted() }
    LaunchedEffect(state.sendError) {
        state.sendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSendError()
        }
    }

    Box(modifier = Modifier.weight(1f)) {
        val selected = state.selected
        when {
            selected != null -> ConversationView(
                friend = selected,
                messages = state.messages,
                isLoading = state.isLoadingMessages,
                myUid = myUid,
                onBack = { viewModel.closeChat() },
                onProfileClick = { onFriendProfileClick(selected.firebaseUid) }
            )
            state.isLoading -> CircularProgressIndicator(
                color = ZenimePrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error.orEmpty(), color = Color.White.copy(alpha = 0.7f))
                TextButton(onClick = { viewModel.loadConversations() }) { Text("Coba lagi", color = ZenimePrimary) }
            }
            state.conversations.isEmpty() -> EmptyConversations(modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.conversations, key = { it.friend.friendshipId }) { item ->
                    ConversationRow(item = item, onClick = { viewModel.openChat(item.friend) })
                }
            }
        }
    }

    DmInputBar(
        value = input,
        onValueChange = { input = it },
        enabled = state.selected != null,
        isSending = state.isSending,
        onSend = {
            viewModel.sendMessage(input)
            input = ""
        },
        modifier = Modifier.navigationBarsPadding().imePadding()
    )
}

@Composable
private fun EmptyConversations(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ZenimeSurfaceDark)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Belum Ada Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tambah teman dari profil user lain, nanti kalian bisa chat private di sini.",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FriendAvatar(friend: FriendDisplay, size: androidx.compose.ui.unit.Dp) {
    if (friend.avatarUrl != null) {
        AsyncImage(
            model = friend.avatarUrl,
            contentDescription = friend.username,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        GeneratedAvatar(seed = friend.firebaseUid, label = friend.username, size = size)
    }
}

@Composable
private fun ConversationRow(item: ConversationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZenimeSurfaceDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FriendAvatar(item.friend, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.friend.username,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.lastMessage?.message ?: "Belum ada pesan",
                color = Color.White.copy(alpha = if (item.unread > 0) 0.9f else 0.55f),
                fontWeight = if (item.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZenimePrimary)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    if (item.unread > 99) "99+" else item.unread.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ConversationView(
    friend: FriendDisplay,
    messages: List<PrivateMessage>,
    isLoading: Boolean,
    myUid: String,
    onBack: () -> Unit,
    onProfileClick: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZenimeSurfaceDark)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke daftar chat", tint = Color.White)
            }
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onProfileClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(friend, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    friend.username,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> CircularProgressIndicator(
                    color = ZenimePrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                messages.isEmpty() -> Text(
                    "Belum ada pesan. Sapa ${friend.username}, yuk!",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        DmBubble(message = msg, isOwn = msg.senderUid == myUid)
                    }
                }
            }
        }
    }
}

@Composable
private fun DmBubble(message: PrivateMessage, isOwn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 16.dp
                    )
                )
                .background(if (isOwn) ZenimePrimary else ZenimeSurfaceDark)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(message.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatDmTime(message.createdAt),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun DmInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZenimeBackgroundDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    if (enabled) "Tulis pesan..." else "Pilih chat untuk lanjut percakapan",
                    color = Color.White.copy(alpha = 0.45f)
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White,
                focusedBorderColor = ZenimePrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                disabledBorderColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = ZenimeSurfaceDark,
                unfocusedContainerColor = ZenimeSurfaceDark,
                disabledContainerColor = ZenimeSurfaceDark,
                cursorColor = ZenimePrimary
            )
        )
        Spacer(Modifier.width(8.dp))
        val canSend = enabled && value.isNotBlank() && !isSending
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) ZenimePrimary else Color.White.copy(alpha = 0.12f))
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Kirim", tint = Color.White)
        }
    }
}
