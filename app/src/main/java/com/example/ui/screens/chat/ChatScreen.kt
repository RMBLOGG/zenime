package com.example.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentFirebaseUid: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Auto-scroll ke pesan paling bawah tiap ada pesan baru masuk.
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ZenimeBackgroundDark,
        topBar = {
            ZenimeHeader(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        ZenimeScreenTitle(title = "Chat Global")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openProfileDialog() }) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Edit Profil",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            color = ZenimePrimary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.messages.isEmpty() -> {
                        Text(
                            text = "Belum ada obrolan. Jadi yang pertama nulis, yuk!",
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.messages,
                                key = { _, item -> item.id }
                            ) { _, message ->
                                ChatBubble(
                                    message = message,
                                    isOwnMessage = message.firebaseUid == currentFirebaseUid,
                                    isDeleting = uiState.deletingMessageId == message.id,
                                    onReply = { viewModel.setReplyTarget(message) },
                                    onDeleteRequest = { pendingDelete = message }
                                )
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            uiState.replyTarget?.let { target ->
                ReplyPreviewBar(
                    username = target.username,
                    message = target.message,
                    onCancel = { viewModel.clearReplyTarget() }
                )
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                cooldownSeconds = uiState.cooldownSeconds,
                isSending = uiState.isSending,
                onSend = {
                    viewModel.sendMessage(input)
                    input = ""
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    }

    if (uiState.isProfileDialogOpen) {
        EditProfileDialog(
            currentUsername = uiState.displayUsername,
            currentAvatarUrl = uiState.displayAvatarUrl,
            isPremium = uiState.isPremium,
            isSaving = uiState.isSavingProfile,
            isUploadingAvatar = uiState.isUploadingAvatar,
            errorMessage = uiState.profileError,
            onPickAvatar = { uri -> viewModel.uploadAvatar(context, uri) },
            onNonPremiumAvatarTap = { viewModel.notifyAvatarRequiresPremium() },
            onSaveUsername = { newName -> viewModel.saveUsername(newName) },
            onDismiss = { viewModel.closeProfileDialog() }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ZenimeSurfaceDark,
            title = { Text("Hapus pesan?", color = Color.White) },
            text = {
                Text(
                    "Pesan ini bakal dihapus buat semua orang di Chat Global.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(target)
                    pendingDelete = null
                }) {
                    Text("Hapus", color = ZenimePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Batal", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    isDeleting: Boolean,
    onReply: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwnMessage) {
            ChatAvatar(url = message.avatarUrl)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (!isOwnMessage) {
                Text(
                    text = message.username,
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isOwnMessage) 14.dp else 2.dp,
                            bottomEnd = if (isOwnMessage) 2.dp else 14.dp
                        )
                    )
                    .background(if (isOwnMessage) ZenimePrimary else ZenimeSurfaceDark)
                    .border(
                        width = 1.dp,
                        color = if (isOwnMessage) Color.Transparent else CardOutlineBorder,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isOwnMessage) 14.dp else 2.dp,
                            bottomEnd = if (isOwnMessage) 2.dp else 14.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (!message.replyToUsername.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text(
                                    text = message.replyToUsername,
                                    color = if (isOwnMessage) Color.White else ZenimePrimary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = message.replyToMessage ?: "",
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = message.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatChatTime(message.createdAt),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                )
                Text(
                    text = "Balas",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = onReply)
                )
                if (isOwnMessage) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = 0.5f),
                            strokeWidth = 1.5.dp,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(10.dp)
                        )
                    } else {
                        Text(
                            text = "Hapus",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable(onClick = onDeleteRequest)
                        )
                    }
                }
            }
        }

        if (isOwnMessage) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatar(url = message.avatarUrl)
        }
    }
}

@Composable
private fun ReplyPreviewBar(
    username: String,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZenimeSurfaceDark)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(ZenimePrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Membalas $username",
                color = ZenimePrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Batal balas",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ChatAvatar(url: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(ZenimeSurfaceDark)
                .border(1.dp, CardOutlineBorder, CircleShape)
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.dp, CardOutlineBorder, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    cooldownSeconds: Int,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && cooldownSeconds == 0 && !isSending

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
            modifier = Modifier.weight(1f),
            placeholder = { Text("Tulis pesan...", color = Color.White.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(20.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ZenimePrimary,
                unfocusedBorderColor = CardOutlineBorder,
                focusedContainerColor = ZenimeSurfaceDark,
                unfocusedContainerColor = ZenimeSurfaceDark,
                cursorColor = ZenimePrimary
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) ZenimePrimary else ZenimeSurfaceDark)
                .border(1.dp, if (canSend) Color.Transparent else CardOutlineBorder, CircleShape)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (cooldownSeconds > 0) {
                Text(
                    text = "$cooldownSeconds",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            } else if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Kirim",
                    tint = if (canSend) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private val chatTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatChatTime(isoTimestamp: String): String {
    return try {
        Instant.parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(chatTimeFormatter)
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun EditProfileDialog(
    currentUsername: String,
    currentAvatarUrl: String?,
    isPremium: Boolean,
    isSaving: Boolean,
    isUploadingAvatar: Boolean,
    errorMessage: String?,
    onPickAvatar: (Uri) -> Unit,
    onNonPremiumAvatarTap: () -> Unit,
    onSaveUsername: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var usernameInput by remember { mutableStateOf(currentUsername) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onPickAvatar(uri)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ZenimeSurfaceDark)
                .border(1.dp, CardOutlineBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Edit Profil Chat",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Avatar + tombol ganti foto.
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(ZenimeBackgroundDark)
                        .border(1.dp, CardOutlineBorder, CircleShape)
                        .clickable {
                            if (isPremium) {
                                imagePicker.launch("image/*")
                            } else {
                                onNonPremiumAvatarTap()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(ZenimePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Ganti foto profil",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (!isPremium) {
                    Text(
                        text = "Upload foto profil khusus member Premium. Kamu tetap bisa pakai foto akun Google.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { if (it.length <= 24) usernameInput = it },
                    label = { Text("Username", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ZenimePrimary,
                        unfocusedBorderColor = CardOutlineBorder,
                        cursorColor = ZenimePrimary
                    )
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = ZenimePrimary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CardOutlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = { onSaveUsername(usernameInput) },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}
