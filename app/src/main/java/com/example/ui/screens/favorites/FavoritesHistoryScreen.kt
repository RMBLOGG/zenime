package com.example.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.AnimeItem
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceVariantDark

import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesHistoryScreen(
    viewModel: FavoritesHistoryViewModel,
    onAnimeClick: (String) -> Unit,
    onPlayEpisodeClick: (episodeId: String, animeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ZenimeHeader(
                title = { ZenimeScreenTitle(title = "Koleksi Saya") }
            )

            // Segmented tab custom — pill capsule dengan tab terpilih diisi warna
            // aksen solid, gantiin TabRow bawaan yang cuma garis bawah tipis
            // generik.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ZenimeSurfaceVariantDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CollectionTab(
                    label = "Favorit",
                    count = favorites.size,
                    icon = Icons.Default.Bookmark,
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    modifier = Modifier.weight(1f)
                )
                CollectionTab(
                    label = "Riwayat",
                    count = watchHistory.size,
                    icon = Icons.Default.History,
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedTabIndex == 0) {
                    // Favorites Grid
                    if (favorites.isEmpty()) {
                        EmptyStateView(
                            title = "Belum Ada Favorit",
                            description = "Tekan ikon bookmark pada halaman detail anime untuk menyimpannya ke favorit.",
                            icon = Icons.Default.Bookmark
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(favorites, key = { it.id }) { fav ->
                                val dummyItem = AnimeItem(
                                    id = fav.id,
                                    title = fav.title,
                                    image_poster = fav.posterUrl,
                                    type = fav.type,
                                    status = fav.status
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    AnimePosterCard(
                                        anime = dummyItem,
                                        onClick = { onAnimeClick(fav.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Tombol hapus favorit -- nempel di pojok
                                    // kanan-atas poster, background bulat
                                    // gelap biar kebaca di atas poster apa pun.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = { viewModel.removeFavorite(fav.id) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus dari Favorit",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Watch History List
                    if (watchHistory.isEmpty()) {
                        EmptyStateView(
                            title = "Belum Ada Riwayat Tontonan",
                            description = "Anime yang kamu tonton akan otomatis muncul di sini.",
                            icon = Icons.Default.History
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(watchHistory, key = { it.animeId }) { historyItem ->
                                WatchHistoryCard(
                                    item = historyItem,
                                    // Tap di mana pun pada kartu -- bukan cuma
                                    // tombol play kecil -- langsung lanjut
                                    // nonton episode terakhir. Halaman detail
                                    // gak relevan lagi di sini karena tujuan
                                    // riwayat emang buat "lanjutin nonton",
                                    // bukan "lihat info anime".
                                    onCardClick = { onPlayEpisodeClick(historyItem.episodeId, historyItem.animeId) },
                                    onResumeClick = { onPlayEpisodeClick(historyItem.episodeId, historyItem.animeId) },
                                    onDeleteClick = { viewModel.deleteHistoryItem(historyItem.animeId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Satu tab di segmented control "Favorit / Riwayat". */
@Composable
private fun CollectionTab(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ZenimePrimary else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryCard(
    item: WatchHistoryEntity,
    onCardClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFrac = if (item.durationMs > 0) {
        (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    // Menit tonton (bukan persen) -- lebih kebayang durasinya ketimbang
    // angka "21%" yang gak jelas itu dari total berapa menit.
    val minutesWatched = (item.progressMs / 60000L).toInt()
    val totalMinutes = if (item.durationMs > 0) (item.durationMs / 60000L).toInt() else null

    // Swipe ke arah mana pun (kiri atau kanan) buat hapus -- tombol trash
    // di kartu tetep ada juga sebagai cara alternatif, dua-duanya manggil
    // onDeleteClick yang sama.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteClick()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> Alignment.Center
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 28.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster dengan badge episode di pojok
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Scrim tipis di bawah poster biar badge episode kebaca
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )
                if (!item.episodeIndex.isNullOrEmpty()) {
                    Text(
                        text = "EP ${item.episodeIndex}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details & Progress Bar
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.animeTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val epLabel = "Episode ${item.episodeIndex ?: ""}${if (!item.episodeTitle.isNullOrEmpty()) " - ${item.episodeTitle}" else ""}"
                Text(
                    text = "$epLabel • ${formatRelativeTime(item.lastUpdated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progressFrac },
                        color = ZenimePrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = if (totalMinutes != null) "$minutesWatched/$totalMinutes mnt" else "$minutesWatched mnt",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZenimePrimary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZenimePrimary,
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                        .clickable { onResumeClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lanjutkan",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    } // tutup content lambda SwipeToDismissBox
}

/** Format selisih waktu jadi teks relatif kayak "2 jam lalu", "Baru saja". */
private fun formatRelativeTime(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit lalu"
        hours < 24 -> "$hours jam lalu"
        days < 7 -> "$days hari lalu"
        else -> "${days / 7} minggu lalu"
    }
}

