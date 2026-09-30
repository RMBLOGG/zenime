package com.example.ui.screens.friends

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.FriendDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.theme.ZenimePrimary

/**
 * Layar Teman: permintaan masuk (terima/tolak), daftar teman (tap buat
 * buka profil, tombol X buat hapus), dan permintaan terkirim (batalin).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onBackClick: () -> Unit,
    onProfileClick: (uid: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearActionError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Teman", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
                uiState.error != null -> ErrorStateView(message = uiState.error!!, onRetry = viewModel::load)
                uiState.friends.isEmpty() && uiState.incoming.isEmpty() && uiState.outgoing.isEmpty() -> EmptyStateView(
                    title = "Belum Ada Teman",
                    description = "Buka profil user lain dari Chat Global, lalu tap Tambah Teman."
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.incoming.isNotEmpty()) {
                        item { SectionLabel("Permintaan Masuk (${uiState.incoming.size})") }
                        items(uiState.incoming, key = { "in_${it.friendshipId}" }) { item ->
                            FriendRow(
                                item = item,
                                onClick = { onProfileClick(item.firebaseUid) }
                            ) {
                                val busy = uiState.busyId == item.friendshipId
                                IconButton(onClick = { viewModel.remove(item) }, enabled = !busy) {
                                    Icon(Icons.Filled.Close, contentDescription = "Tolak", tint = Color(0xFFE53935))
                                }
                                IconButton(onClick = { viewModel.accept(item) }, enabled = !busy) {
                                    Icon(Icons.Filled.Check, contentDescription = "Terima", tint = ZenimePrimary)
                                }
                            }
                        }
                    }
                    if (uiState.friends.isNotEmpty()) {
                        item { SectionLabel("Teman (${uiState.friends.size})") }
                        items(uiState.friends, key = { "fr_${it.friendshipId}" }) { item ->
                            FriendRow(
                                item = item,
                                onClick = { onProfileClick(item.firebaseUid) }
                            ) {
                                IconButton(
                                    onClick = { viewModel.remove(item) },
                                    enabled = uiState.busyId != item.friendshipId
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Hapus teman",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.outgoing.isNotEmpty()) {
                        item { SectionLabel("Permintaan Terkirim (${uiState.outgoing.size})") }
                        items(uiState.outgoing, key = { "out_${it.friendshipId}" }) { item ->
                            FriendRow(
                                item = item,
                                onClick = { onProfileClick(item.firebaseUid) }
                            ) {
                                TextButton(
                                    onClick = { viewModel.remove(item) },
                                    enabled = uiState.busyId != item.friendshipId
                                ) { Text("Batalkan") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun FriendRow(
    item: FriendDisplay,
    onClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.avatarUrl != null) {
            AsyncImage(
                model = item.avatarUrl,
                contentDescription = item.username,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        } else {
            GeneratedAvatar(seed = item.firebaseUid, label = item.username, size = 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.username,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
