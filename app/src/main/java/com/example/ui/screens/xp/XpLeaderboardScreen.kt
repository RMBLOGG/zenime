package com.example.ui.screens.xp

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.UserXpDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark

/**
 * Leaderboard XP -- desain podium top-3 + list ala referensi yang dikasih
 * user. Fungsinya TETEP sama kayak sebelumnya (total_xp kumulatif, gak
 * reset harian) -- cuma tampilannya yang di-samain, gak ada tab
 * Harian/Mingguan/Bulanan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XpLeaderboardScreen(
    viewModel: XpLeaderboardViewModel,
    myFirebaseUid: String?,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFF0B0B12),
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard XP", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0B12))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ZenimePrimary)
                    }
                }
                uiState.error != null -> {
                    ErrorStateView(message = uiState.error!!, onRetry = viewModel::retry)
                }
                uiState.entries.isEmpty() -> {
                    EmptyStateView(
                        title = "Belum Ada yang Nonton",
                        description = "Jadi yang pertama naik XP hari ini!"
                    )
                }
                else -> {
                    val top3 = uiState.entries.take(3)
                    val rest = uiState.entries.drop(3)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (top3.isNotEmpty()) {
                            item { PodiumTop3(entries = top3, myFirebaseUid = myFirebaseUid) }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                        itemsIndexed(rest) { index, entry ->
                            XpLeaderboardRow(
                                rank = index + 4,
                                entry = entry,
                                isMe = entry.firebaseUid == myFirebaseUid
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * Tab "Harian / Mingguan / Bulanan" -- cuma Harian yang bisa dipencet &
 * ganti tampilan. Dua lainnya SENGAJA gak ada onClick logic apa-apa (belum
 * diminta), dibiarin keliatan tapi redup biar gak dikira rusak/ke-skip.
 */
@Composable
private fun PodiumTop3(entries: List<UserXpDisplay>, myFirebaseUid: String?) {
    // Urutan tampil: #2 (kiri) - #1 (tengah, lebih tinggi) - #3 (kanan) --
    // sama kayak podium referensi.
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ZenimePrimary.copy(alpha = 0.25f), Color(0xFF17131F))
                )
            )
            .padding(vertical = 20.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            second?.let {
                PodiumCard(
                    entry = it,
                    rank = 2,
                    isMe = it.firebaseUid == myFirebaseUid,
                    avatarSize = 64.dp,
                    ringColor = Color(0xFFC0C0C0)
                )
            }
            first?.let {
                PodiumCard(
                    entry = it,
                    rank = 1,
                    isMe = it.firebaseUid == myFirebaseUid,
                    avatarSize = 80.dp,
                    ringColor = Color(0xFFFFD700),
                    showCrown = true
                )
            }
            third?.let {
                PodiumCard(
                    entry = it,
                    rank = 3,
                    isMe = it.firebaseUid == myFirebaseUid,
                    avatarSize = 64.dp,
                    ringColor = Color(0xFFCD7F32)
                )
            }
        }
    }
}

@Composable
private fun PodiumCard(
    entry: UserXpDisplay,
    rank: Int,
    isMe: Boolean,
    avatarSize: Dp,
    ringColor: Color,
    showCrown: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        if (showCrown) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(2.dp))
        }

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(ZenimeSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (!entry.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    GeneratedAvatar(seed = entry.firebaseUid, label = entry.username, size = avatarSize)
                }
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = TextUnit(11f, TextUnitType.Sp))
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.username + if (isMe) " (Kamu)" else "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (entry.clanTag != null) {
            Spacer(Modifier.height(2.dp))
            ClanTagChip(entry.clanTag)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${entry.totalXp} XP",
            color = ringColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ClanTagChip(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ZenimePrimary)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = tag,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = TextUnit(9f, TextUnitType.Sp)
            )
        )
    }
}

@Composable
private fun XpLeaderboardRow(rank: Int, entry: UserXpDisplay, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) ZenimePrimary.copy(alpha = 0.15f) else ZenimeSurfaceDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(40.dp)
        )

        if (!entry.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = entry.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape)
            )
        } else {
            GeneratedAvatar(seed = entry.firebaseUid, label = entry.username, size = 44.dp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.username + if (isMe) " (Kamu)" else "",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.clanTag != null) {
                    ClanTagChip(entry.clanTag)
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1F6F4A))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Lvl ${entry.level}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = TextUnit(9f, TextUnitType.Sp)
                        )
                    )
                }
            }
        }

        Text(
            text = "${entry.totalXp} XP",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFFFC107)
        )
    }
}
