package com.example.ui.screens.comments

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.data.model.EpisodeComment
import com.example.ui.components.GeneratedAvatar
import com.example.ui.components.LevelBadge
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PremiumBlue = Color(0xFF3897F0)
private val PinnedGold = Color(0xFFE8A317)

/**
 * Sheet utama "N Comments" -- dipicu dari tombol "Komentar" di halaman
 * episode. Nge-render header, tab sort, input komentar baru, & daftar
 * komentar top-level. Tap "Reply" di sebuah komentar buka [CommentThreadSheet]
 * di atasnya (stacked), bukan pindah halaman.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeCommentsSheet(
    viewModel: CommentsViewModel,
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121218),
        dragHandle = { CommentsDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxSize(0.94f).imePadding()) {
            // Header: jumlah komentar + ikon "Atur" (belum ada menu lanjutan).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.totalCount} Komentar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    Toast.makeText(context, "Pengaturan komentar segera hadir", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Atur", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            // Tab sort: Top Comment / Terbaru.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortTabChip(
                    label = "Top Comment",
                    selected = state.sortMode == CommentSortMode.TOP_COMMENT,
                    onClick = { viewModel.setSortMode(CommentSortMode.TOP_COMMENT) }
                )
                SortTabChip(
                    label = "Terbaru",
                    selected = state.sortMode == CommentSortMode.TERBARU,
                    onClick = { viewModel.setSortMode(CommentSortMode.TERBARU) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Input komentar baru.
            var inputText by rememberSaveable { mutableStateOf("") }
            var pinnedToggle by rememberSaveable { mutableStateOf(false) }
            CommentInputRow(
                avatarUrl = state.myAvatarUrl,
                avatarSeed = state.myFirebaseUid,
                avatarLabel = state.myUsername,
                text = inputText,
                onTextChange = { inputText = it },
                placeholder = "Tulis komentar..",
                isSending = state.isSending,
                showPremiumToggle = true,
                premiumToggleActive = pinnedToggle,
                onPremiumToggleClick = {
                    if (state.isPremium) {
                        pinnedToggle = !pinnedToggle
                    } else {
                        onUpgradeClick()
                    }
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.postTopLevelComment(inputText, pinnedToggle)
                        inputText = ""
                        pinnedToggle = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Daftar komentar top-level.
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ZenimePrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                    state.sortedTopLevel.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Belum ada komentar. Jadi yang pertama!",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                        ) {
                            items(state.sortedTopLevel, key = { it.id }) { comment ->
                                CommentItem(
                                    comment = comment,
                                    isOwn = comment.firebaseUid == state.myFirebaseUid,
                                    isPremiumSender = comment.firebaseUid in state.premiumUids,
                                    level = state.levelsByUid[comment.firebaseUid],
                                    userNumber = state.userNumbersByUid[comment.firebaseUid],
                                    avatarUrlOverride = state.avatarUrlsByUid[comment.firebaseUid],
                                    replyCount = state.repliesByParent[comment.id]?.size ?: 0,
                                    isDeleting = state.deletingCommentId == comment.id,
                                    onReplyClick = { viewModel.openThread(comment.id) },
                                    onDeleteClick = { viewModel.deleteComment(comment) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet "Threads" -- stacked di atas sheet utama, dibuka pas
    // user nge-tap "Reply" di salah satu komentar.
    if (state.openThreadParentId != null) {
        val parent = state.topLevel.find { it.id == state.openThreadParentId }
        CommentThreadSheet(
            parent = parent,
            replies = state.repliesByParent[state.openThreadParentId] ?: emptyList(),
            myFirebaseUid = state.myFirebaseUid,
            myAvatarUrl = state.myAvatarUrl,
            myUsername = state.myUsername,
            isSending = state.isSending,
            replyTarget = state.replyTarget,
            premiumUids = state.premiumUids,
            levelsByUid = state.levelsByUid,
            userNumbersByUid = state.userNumbersByUid,
            avatarUrlsByUid = state.avatarUrlsByUid,
            deletingCommentId = state.deletingCommentId,
            onSetReplyTarget = viewModel::setReplyTarget,
            onSend = viewModel::postReply,
            onDeleteClick = viewModel::deleteComment,
            onDismiss = viewModel::closeThread
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentThreadSheet(
    parent: EpisodeComment?,
    replies: List<EpisodeComment>,
    myFirebaseUid: String,
    myAvatarUrl: String?,
    myUsername: String,
    isSending: Boolean,
    replyTarget: EpisodeComment?,
    premiumUids: Set<String>,
    levelsByUid: Map<String, Int>,
    userNumbersByUid: Map<String, Long>,
    avatarUrlsByUid: Map<String, String>,
    deletingCommentId: Long?,
    onSetReplyTarget: (EpisodeComment?) -> Unit,
    onSend: (String) -> Unit,
    onDeleteClick: (EpisodeComment) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by rememberSaveable(parent?.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121218),
        dragHandle = { CommentsDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxSize(0.9f).imePadding()) {
            Text(
                text = "Threads",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (parent != null) {
                    item(key = "parent-${parent.id}") {
                        CommentItem(
                            comment = parent,
                            isOwn = parent.firebaseUid == myFirebaseUid,
                            isPremiumSender = parent.firebaseUid in premiumUids,
                            level = levelsByUid[parent.firebaseUid],
                            userNumber = userNumbersByUid[parent.firebaseUid],
                            avatarUrlOverride = avatarUrlsByUid[parent.firebaseUid],
                            replyCount = null,
                            isDeleting = deletingCommentId == parent.id,
                            onReplyClick = { onSetReplyTarget(null) },
                            onDeleteClick = { onDeleteClick(parent) }
                        )
                        androidx.compose.material3.HorizontalDivider(color = CardOutlineBorder, thickness = 0.6.dp)
                    }
                }
                items(replies, key = { it.id }) { reply ->
                    CommentItem(
                        comment = reply,
                        isOwn = reply.firebaseUid == myFirebaseUid,
                        isPremiumSender = reply.firebaseUid in premiumUids,
                        level = levelsByUid[reply.firebaseUid],
                        userNumber = userNumbersByUid[reply.firebaseUid],
                        avatarUrlOverride = avatarUrlsByUid[reply.firebaseUid],
                        replyCount = null,
                        isDeleting = deletingCommentId == reply.id,
                        onReplyClick = { onSetReplyTarget(reply) },
                        onDeleteClick = { onDeleteClick(reply) }
                    )
                }
            }

            AnimatedVisibility(visible = replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Membalas @${replyTarget?.username ?: ""}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onSetReplyTarget(null) }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Batal balas", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }
            }

            CommentInputRow(
                avatarUrl = myAvatarUrl,
                avatarSeed = myFirebaseUid,
                avatarLabel = myUsername,
                text = inputText,
                onTextChange = { inputText = it },
                placeholder = "Tulis balasan...",
                isSending = isSending,
                showPremiumToggle = false,
                premiumToggleActive = false,
                onPremiumToggleClick = {},
                onSend = {
                    if (inputText.isNotBlank()) {
                        onSend(inputText)
                        inputText = ""
                    }
                }
            )
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun CommentInputRow(
    avatarUrl: String?,
    avatarSeed: String,
    avatarLabel: String,
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    isSending: Boolean,
    showPremiumToggle: Boolean,
    premiumToggleActive: Boolean,
    onPremiumToggleClick: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommentAvatar(url = avatarUrl, seed = avatarSeed, label = avatarLabel, size = 32.dp)

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZenimePrimary.copy(alpha = 0.6f),
                unfocusedBorderColor = CardOutlineBorder,
                cursorColor = ZenimePrimary
            )
        )

        if (showPremiumToggle) {
            IconButton(
                onClick = onPremiumToggleClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (premiumToggleActive) PinnedGold else Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Sorot komentar Premium",
                    tint = if (premiumToggleActive) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val canSend = text.isNotBlank() && !isSending
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (canSend) ZenimePrimary else Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Kirim",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CommentItem(
    comment: EpisodeComment,
    isOwn: Boolean,
    isPremiumSender: Boolean,
    level: Int?,
    userNumber: Long?,
    avatarUrlOverride: String?,
    replyCount: Int?,
    isDeleting: Boolean,
    onReplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        CommentAvatar(
            url = avatarUrlOverride ?: comment.avatarUrl,
            seed = comment.firebaseUid,
            label = comment.username,
            size = 34.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.username,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 3.dp)
                )
                if (isPremiumSender) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Premium",
                        tint = PremiumBlue,
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(15.dp)
                    )
                }
                if (userNumber != null) {
                    Text(
                        text = "#$userNumber",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatCommentRelativeTime(comment.createdAt),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
                if (isOwn) {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opsi", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Hapus") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            if (level != null) {
                Spacer(modifier = Modifier.height(3.dp))
                LevelBadge(level = level)
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!comment.replyToUsername.isNullOrBlank()) {
                Text(
                    text = "@${comment.replyToUsername}",
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Text(
                text = comment.comment,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (replyCount != null && replyCount > 0) "Reply ($replyCount)" else "Reply",
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onReplyClick() }
                )
                if (isDeleting) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(url: String?, seed: String, label: String, size: androidx.compose.ui.unit.Dp) {
    if (url.isNullOrBlank()) {
        GeneratedAvatar(seed = seed, label = label, size = size, borderColor = CardOutlineBorder)
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, CardOutlineBorder, CircleShape)
        )
    }
}

@Composable
private fun SortTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
        color = if (selected) Color.Black else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun CommentsDragHandle() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

private val commentTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

private fun formatCommentRelativeTime(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val diffMs = (System.currentTimeMillis() - instant.toEpochMilli()).coerceAtLeast(0)
        val minutes = diffMs / 60_000
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            hours < 24 -> "$hours jam lalu"
            days < 7 -> "$days hari lalu"
            days < 30 -> "${days / 7} minggu lalu"
            else -> instant.atZone(ZoneId.systemDefault()).format(commentTimeFormatter)
        }
    } catch (e: Exception) {
        ""
    }
}

