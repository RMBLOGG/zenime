package com.example.ui.screens.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.model.EpisodeItem
import com.example.ui.theme.ZenimePrimary
import com.example.util.episodeIndexValue
import com.example.util.isEpisodeLocked

private const val EPISODE_COLUMNS = 3

/**
 * Daftar episode bentuk grid 3 kolom (thumbnail persegi + nomor di pojok).
 *
 * Tiap baris jadi satu item LazyColumn, jadi anime dengan ratusan episode tetap
 * ringan: yang dikomposisi & dimuat gambarnya cuma baris yang terlihat.
 *
 * Aksi:
 *  - ketuk         -> putar episode
 *  - tekan lama    -> download (atau hapus download kalau sudah selesai)
 */
fun LazyListScope.episodeGridItems(
    episodes: List<EpisodeItem>,
    fallbackImageUrl: String?,
    watchedEpisodeId: String?,
    downloads: List<DownloadedEpisodeEntity>,
    isPremium: Boolean,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onDownloadClick: (EpisodeItem) -> Unit,
    onDeleteDownloadClick: (EpisodeItem) -> Unit,
    tabSlide: TabSlide? = null
) {
    // Index episode paling baru dari seluruh daftar -- dipakai buat nentuin
    // LOCKED_LATEST_EPISODES_COUNT episode terbaru mana yang dikunci non-premium.
    val totalEpisodes = episodes.mapNotNull { episodeIndexValue(it.index) }.maxOrNull() ?: episodes.size
    val rows = episodes.chunked(EPISODE_COLUMNS)
    itemsIndexed(rows, key = { _, row -> "ep_row_${row.first().id}" }) { _, row ->
        Row(
            modifier = Modifier
                .tabSlide(tabSlide)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { ep ->
                Box(modifier = Modifier.weight(1f)) {
                    EpisodeGridCard(
                        episode = ep,
                        imageUrl = ep.resolvedImageUrl ?: fallbackImageUrl,
                        isWatched = watchedEpisodeId == ep.id,
                        isLocked = isEpisodeLocked(ep.index, totalEpisodes, isPremium),
                        downloadEntry = downloads.find { it.episodeId == ep.id },
                        onClick = { onEpisodeClick(ep) },
                        onDownloadClick = { onDownloadClick(ep) },
                        onDeleteDownloadClick = { onDeleteDownloadClick(ep) }
                    )
                }
            }
            repeat(EPISODE_COLUMNS - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeGridCard(
    episode: EpisodeItem,
    imageUrl: String?,
    isWatched: Boolean,
    isLocked: Boolean,
    downloadEntry: DownloadedEpisodeEntity?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // Episode terkunci Premium tidak bisa di-download.
                    if (!isLocked) {
                        when (downloadEntry?.status) {
                            DownloadStatus.COMPLETED -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteDownloadClick()
                            }
                            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> Unit
                            DownloadStatus.FAILED, null -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDownloadClick()
                            }
                        }
                    }
                }
            )
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Belum ada thumbnail.
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        if (isLocked) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        // Badge kiri atas: gembok Premium ATAU status download.
        when {
            isLocked -> CornerBadge(background = ZenimePrimary) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Episode Premium",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
            downloadEntry != null -> CornerBadge(background = Color.Black.copy(alpha = 0.6f)) {
                when (downloadEntry.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        val progress = if (downloadEntry.totalBytes > 0) {
                            (downloadEntry.downloadedBytes.toFloat() /
                                downloadEntry.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(14.dp),
                            color = ZenimePrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    DownloadStatus.COMPLETED -> Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = "Sudah didownload, tekan lama untuk hapus",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp)
                    )
                    DownloadStatus.FAILED -> Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Download gagal, tekan lama untuk coba lagi",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (episode.is_new == "1") {
            NewRibbon(modifier = Modifier.align(Alignment.TopEnd))
        }

        // Nomor episode: "tab" di pojok kanan bawah, warnanya sama dengan latar
        // halaman sehingga tampak seperti potongan kartu.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 18.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Text(
                text = episode.index ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = if (isWatched) ZenimePrimary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }

        // Episode yang terakhir ditonton diberi bingkai.
        if (isWatched) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, ZenimePrimary, shape)
            )
        }
    }
}

@Composable
private fun CornerBadge(
    background: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Pita segitiga "NEW" di pojok kanan atas. */
@Composable
private fun NewRibbon(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(54.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.28f, 0f)
                lineTo(w, 0f)
                lineTo(w, w * 0.72f)
                close()
            }
            drawPath(path, color = ZenimePrimary)
        }
        Text(
            text = "NEW",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            modifier = Modifier
                .offset(x = 29.dp, y = 8.dp)
                .rotate(45f)
        )
    }
}
