package com.example.ui.screens.detail

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.common.Result
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.model.EpisodeItem
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerEpisodeList
import com.example.util.isDownloadAllowed
import com.example.util.isEpisodeLocked
import com.example.ui.components.ShimmerHorizontalSection
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.StarYellow
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onEpisodeClick: (episodeId: String, episodeTitle: String) -> Unit,
    onUpgradeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val episodesState by viewModel.episodesState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val previewUrl by viewModel.previewUrl.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadErrorMessage by viewModel.downloadErrorMessage.collectAsStateWithLifecycle()

    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var episodeToDeleteDownload by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    LaunchedEffect(downloadErrorMessage) {
        val message = downloadErrorMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearDownloadError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = detailState) {
                is Result.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShimmerPosterItem()
                        Spacer(modifier = Modifier.height(16.dp))
                        ShimmerHorizontalSection()
                    }
                }
                is Result.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = {
                            viewModel.loadDetail()
                            viewModel.loadEpisodes()
                        }
                    )
                }
                is Result.Success -> {
                    val anime = state.data
                    val episodesList = (episodesState as? Result.Success)?.data ?: emptyList()

                    val targetEpisode = if (watchHistory != null && episodesList.isNotEmpty()) {
                        episodesList.find { it.id == watchHistory?.episodeId } ?: episodesList.firstOrNull()
                    } else {
                        episodesList.firstOrNull()
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 36.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. FULL-BLEED Poster / Artwork at top 55% of screen
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp)
                            ) {
                                // Full-bleed cover/poster artwork -- otomatis
                                // ganti ke preview 15 detik (dari episode 1,
                                // ADA SUARA) begitu link-nya siap, terus balik
                                // lagi ke poster statis pas kelar.
                                HeroPreviewPlayer(
                                    posterUrl = anime.image_cover ?: anime.image_poster,
                                    previewUrl = previewUrl,
                                    contentDescription = anime.title,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Vertical Gradient Overlay from #0B0E14 at bottom to transparent at top
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.5f),
                                                    Color.Transparent,
                                                    Color(0xFF0B0E14).copy(alpha = 0.7f),
                                                    Color(0xFF0B0E14)
                                                )
                                            )
                                        )
                                )

                                // Top navigation action icons (Back & Bookmark)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .align(Alignment.TopCenter),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = onBackClick,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Kembali",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.toggleFavorite() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .testTag("favorite_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Favorit",
                                            tint = if (isFavorite) ZenimePrimary else Color.White
                                        )
                                    }
                                }

                                // Anime Title and Metadata stacked over gradient
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, end = 80.dp, bottom = 12.dp)
                                ) {
                                    Text(
                                        text = anime.title ?: "Tanpa Judul",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 26.sp,
                                            lineHeight = 32.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Brief Info Row (Year • Type • Rating)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = StarYellow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "4.8",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )

                                        val metadataText = listOfNotNull(
                                            anime.year?.takeIf { it.isNotBlank() },
                                            anime.type?.takeIf { it.isNotBlank() },
                                            anime.status?.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")

                                        if (metadataText.isNotBlank()) {
                                            Text(
                                                text = "• $metadataText",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                // Large Floating Circular Crimson Play Button on bottom right
                                if (targetEpisode != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ZenimePrimary,
                                        shadowElevation = 14.dp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 20.dp, bottom = 12.dp)
                                            .size(64.dp)
                                            .clickable {
                                                onEpisodeClick(targetEpisode.id, targetEpisode.title ?: "Episode ${targetEpisode.index}")
                                            }
                                            .testTag("play_button")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Mainkan Episode",
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Genre Chips Bar (Pill Shape)
                        anime.genre?.let { genreStr ->
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                val genreList = genreStr.split(",").map { it.trim() }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(genreList) { g ->
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.border(1.dp, CardOutlineBorder, CircleShape)
                                        ) {
                                            Text(
                                                text = g,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Synopsis
                        anime.synopsis?.let { synopsisText ->
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Text(
                                        text = "Sinopsis",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = synopsisText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.animateContentSize()
                                    )
                                    Text(
                                        text = if (isSynopsisExpanded) "Sembunyikan" else "Baca Selengkapnya...",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ZenimePrimary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                    )
                                }
                            }
                        }

                        // 4. Episode List Section Header
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (episodesState is Result.Loading) {
                                        "Daftar Episode"
                                    } else {
                                        "Daftar Episode (${episodesList.size})"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 5. Episode Card List (Horizontal Small Cards)
                        if (episodesState is Result.Loading) {
                            item {
                                ShimmerEpisodeList()
                            }
                        } else if (episodesList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Episode belum tersedia",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(episodesList, key = { it.id }) { ep ->
                                val isWatched = watchHistory?.episodeId == ep.id
                                val downloadEntry = downloads.find { it.episodeId == ep.id }
                                EpisodeHorizontalCard(
                                    episode = ep,
                                    posterUrl = ep.resolvedImageUrl ?: anime.image_cover ?: anime.image_poster,
                                    isWatched = isWatched,
                                    isLocked = isEpisodeLocked(ep.index, isPremium),
                                    downloadEntry = downloadEntry,
                                    isDownloadAllowed = isDownloadAllowed(isPremium),
                                    onDownloadClick = {
                                        if (isDownloadAllowed(isPremium)) {
                                            viewModel.downloadEpisode(ep)
                                        } else {
                                            onUpgradeClick()
                                        }
                                    },
                                    onDeleteDownloadClick = { episodeToDeleteDownload = ep.id },
                                    onClick = {
                                        onEpisodeClick(ep.id, ep.title ?: "Episode ${ep.index}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val episodeIdPendingDelete = episodeToDeleteDownload
    if (episodeIdPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { episodeToDeleteDownload = null },
            title = { Text("Hapus download?") },
            text = { Text("Episode ini bakal dihapus dari penyimpanan offline. Kamu bisa download ulang kapan saja.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownload(episodeIdPendingDelete)
                    episodeToDeleteDownload = null
                }) {
                    Text("Hapus", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { episodeToDeleteDownload = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun EpisodeHorizontalCard(
    episode: EpisodeItem,
    posterUrl: String?,
    isWatched: Boolean,
    onClick: () -> Unit,
    isLocked: Boolean = false,
    downloadEntry: DownloadedEpisodeEntity? = null,
    isDownloadAllowed: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .border(
                width = 1.dp,
                color = if (isWatched) ZenimePrimary.copy(alpha = 0.5f) else CardOutlineBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail (Aspect 16:9)
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isLocked -> ZenimePrimary
                            isWatched -> ZenimePrimary
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                isLocked -> {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Episode Premium",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                isWatched -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Watched",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = ZenimePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Episode ${episode.index ?: ""}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (episode.is_new == "1") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ZenimePrimary
                        ) {
                            Text(
                                text = "BARU",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                val epTitle = episode.title
                if (!epTitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tombol download offline -- cuma ditampilin kalau episode ini
            // gak sedang terkunci Premium (biar gak dobel sama ikon gembok
            // di thumbnail), dan aksinya sendiri masih dicek isDownloadAllowed
            // (fitur download-nya sendiri exclusive Premium).
            if (!isLocked) {
                EpisodeDownloadButton(
                    entry = downloadEntry,
                    onDownloadClick = onDownloadClick,
                    onDeleteClick = onDeleteDownloadClick,
                    isDownloadAllowed = isDownloadAllowed
                )
            }
        }
    }
}

@Composable
private fun EpisodeDownloadButton(
    entry: DownloadedEpisodeEntity?,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDownloadAllowed: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable {
                when (entry?.status) {
                    DownloadStatus.COMPLETED -> onDeleteClick()
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> Unit
                    DownloadStatus.FAILED, null -> onDownloadClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (entry?.status) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                val progress = if (entry.totalBytes > 0) {
                    (entry.downloadedBytes.toFloat() / entry.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    color = ZenimePrimary,
                    strokeWidth = 2.dp
                )
            }
            DownloadStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Sudah didownload, ketuk buat hapus",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
            }
            DownloadStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Download gagal, ketuk buat coba lagi",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(18.dp)
                )
            }
            null -> {
                Icon(
                    imageVector = if (isDownloadAllowed) Icons.Default.DownloadForOffline else Icons.Default.Lock,
                    contentDescription = if (isDownloadAllowed) {
                        "Download buat nonton offline"
                    } else {
                        "Download episode khusus Premium"
                    },
                    tint = if (isDownloadAllowed) MaterialTheme.colorScheme.onSurfaceVariant else ZenimePrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private const val PREVIEW_DURATION_MS = 15_000L

/**
 * Poster statis di hero section, auto-ganti ke video preview (clip dari
 * episode 1, BUKAN trailer resmi -- API upstream gak nyediain trailer)
 * begitu link-nya siap. Ada suara langsung (bukan muted), muter
 * [PREVIEW_DURATION_MS], terus balik lagi ke poster statis.
 *
 * Kalau [previewUrl] gagal/gak ada (anime gak punya episode 1, atau
 * network error), tetep nampilin poster statis aja -- gak ada error yang
 * kelihatan ke user, karena ini emang cuma "bonus" bukan fitur wajib.
 */
@OptIn(UnstableApi::class)
@Composable
private fun HeroPreviewPlayer(
    posterUrl: String?,
    previewUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var hasFinishedPreview by remember(previewUrl) { mutableStateOf(false) }
    val showPreview = previewUrl != null && !hasFinishedPreview

    Box(modifier = modifier) {
        // Poster statis -- selalu di-render di belakang, biar begitu preview
        // kelar (atau belum siap sama sekali) transisinya mulus tanpa flash.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showPreview,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (previewUrl != null) {
                HeroPreviewVideo(
                    previewUrl = previewUrl,
                    onFinished = { hasFinishedPreview = true }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HeroPreviewVideo(
    previewUrl: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(previewUrl) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://animeinweb.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )
        val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(previewUrl)))

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            volume = 1f
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    // Stop otomatis setelah PREVIEW_DURATION_MS, terlepas video-nya lebih
    // panjang dari itu atau enggak (episode utuh biasanya jauh lebih lama).
    LaunchedEffect(exoPlayer) {
        delay(PREVIEW_DURATION_MS)
        onFinished()
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    )
}
