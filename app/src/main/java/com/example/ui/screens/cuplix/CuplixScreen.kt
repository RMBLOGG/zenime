package com.example.ui.screens.cuplix

import android.net.Uri
import androidx.annotation.OptIn as UnstableOptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.common.Result
import com.example.data.model.CuplixItem
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Feed Cuplix ala TikTok: satu klip memenuhi layar, geser ke atas/bawah buat
 * pindah. Cuplix = potongan waktu sebuah episode (timeStart..timeEnd), jadi
 * video-nya diputar dari MP4 episode aslinya: mulai di timeStart lalu
 * dilooping balik ke timeStart saat mencapai timeEnd.
 *
 * Cukup SATU ExoPlayer untuk seluruh layar; PlayerView cuma dipasang di
 * halaman yang sedang aktif (settledPage), halaman lain nampilin thumbnail.
 */
@UnstableOptIn(UnstableApi::class)
@Composable
fun CuplixScreen(
    viewModel: CuplixViewModel,
    startClipId: String? = null,
    onBackClick: () -> Unit,
    onAnimeClick: (animeId: String) -> Unit,
    onEpisodeClick: (episodeId: String, animeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var firstFrameShown by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var userPaused by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }

    // Header sama dengan PlayerScreen supaya server video menerima request-nya.
    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory().setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://animeinweb.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        )
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameShown = true
            }

            override fun onPlayerError(error: PlaybackException) {
                loadError = "Video gagal diputar. Periksa koneksi lalu coba lagi."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Jeda saat app ke background, lanjut saat balik (kecuali user sendiri yang pause).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!userPaused && exoPlayer.mediaItemCount > 0) exoPlayer.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState(pageCount = { state.items.size })
    val activeItem: CuplixItem? = state.items.getOrNull(pagerState.settledPage)

    // Dibuka dari strip beranda -> loncat ke klip yang diketuk (sekali saja;
    // rememberSaveable supaya tidak loncat lagi saat balik dari layar lain).
    var jumpedToStart by rememberSaveable { mutableStateOf(startClipId == null) }
    LaunchedEffect(state.items.size) {
        if (!jumpedToStart && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.id == startClipId }
            if (index >= 0) pagerState.scrollToPage(index)
            jumpedToStart = true
        }
    }

    // Ganti urutan -> balik ke klip pertama. Hanya saat urutan BERUBAH, bukan
    // saat layar dibuka lagi (posisi terakhir dipulihkan oleh pager state).
    var lastSort by remember { mutableStateOf(state.sort) }
    LaunchedEffect(state.sort) {
        if (state.sort != lastSort) {
            lastSort = state.sort
            if (state.items.isNotEmpty()) pagerState.scrollToPage(0)
        }
    }

    // Muat batch berikutnya saat tinggal 3 klip lagi.
    LaunchedEffect(pagerState.settledPage, state.items.size) {
        if (state.items.isNotEmpty() && pagerState.settledPage >= state.items.size - 3) {
            viewModel.loadMore()
        }
    }

    // Siapkan & putar klip aktif; loop di potongan waktunya.
    LaunchedEffect(activeItem?.id, retryToken) {
        // Hentikan klip sebelumnya dulu (juga saat daftar dikosongkan karena ganti urutan).
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val item = activeItem ?: return@LaunchedEffect
        firstFrameShown = false
        loadError = null
        userPaused = false

        val episodeId = item.idEpisode
        if (episodeId.isNullOrBlank()) {
            loadError = "Klip ini tidak punya episode."
            return@LaunchedEffect
        }

        when (val result = viewModel.videoUrl(episodeId)) {
            is Result.Success -> {
                val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(result.data)))
                exoPlayer.setMediaSource(source)
                exoPlayer.seekTo(item.startMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
            is Result.Error -> {
                loadError = result.message
                return@LaunchedEffect
            }
            is Result.Loading -> return@LaunchedEffect
        }

        // Siapkan URL klip berikutnya biar geser terasa cepat.
        state.items.getOrNull(pagerState.settledPage + 1)?.idEpisode
            ?.takeIf { it.isNotBlank() }
            ?.let { viewModel.prefetch(it) }

        val start = item.startMs
        val end = item.endMs
        while (true) {
            delay(150)
            val ended = exoPlayer.playbackState == Player.STATE_ENDED
            val pastEnd = end > 0 && exoPlayer.isPlaying && exoPlayer.currentPosition >= end
            if (ended || pastEnd) {
                exoPlayer.seekTo(start)
                exoPlayer.playWhenReady = !userPaused
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.items.isEmpty()) {
            CuplixEmptyState(
                isLoading = state.isLoading,
                error = state.error,
                onRetry = { viewModel.retry() }
            )
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> state.items.getOrNull(index)?.id ?: index }
            ) { page ->
                val item = state.items.getOrNull(page) ?: return@VerticalPager
                val isActive = page == pagerState.settledPage
                CuplixPageContent(
                    item = item,
                    isActive = isActive,
                    exoPlayer = exoPlayer,
                    firstFrameShown = firstFrameShown,
                    loadError = if (isActive) loadError else null,
                    userPaused = isActive && userPaused,
                    onTogglePlay = {
                        if (exoPlayer.playWhenReady) {
                            exoPlayer.playWhenReady = false
                            userPaused = true
                        } else {
                            exoPlayer.playWhenReady = true
                            userPaused = false
                        }
                    },
                    onRetry = {
                        item.idEpisode?.let { viewModel.forgetUrl(it) }
                        retryToken++
                    },
                    onAnimeClick = onAnimeClick,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }

        // Bar atas: kembali + urutan.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SortPill("Populer", state.sort == CuplixViewModel.SORT_POPULAR) {
                viewModel.setSort(CuplixViewModel.SORT_POPULAR)
            }
            Spacer(modifier = Modifier.width(6.dp))
            SortPill("Terbaru", state.sort == CuplixViewModel.SORT_NEW) {
                viewModel.setSort(CuplixViewModel.SORT_NEW)
            }
            Spacer(modifier = Modifier.width(6.dp))
            SortPill("Terlama", state.sort == CuplixViewModel.SORT_OLD) {
                viewModel.setSort(CuplixViewModel.SORT_OLD)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CuplixPageContent(
    item: CuplixItem,
    isActive: Boolean,
    exoPlayer: ExoPlayer,
    firstFrameShown: Boolean,
    loadError: String?,
    userPaused: Boolean,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onEpisodeClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val animeId = item.idMovie.orEmpty()
    val episodeId = item.idEpisode.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlay
            )
    ) {
        if (isActive) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                update = { it.player = exoPlayer },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Thumbnail menutupi layar sampai frame pertama video tampil.
        if (!(isActive && firstFrameShown)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.urlThumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isActive && !firstFrameShown && loadError == null) {
            CircularProgressIndicator(
                color = ZenimePrimary,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        if (userPaused) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Lanjutkan",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
            )
        }

        if (loadError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = loadError,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActionChip(text = "Coba lagi", onClick = onRetry)
            }
        }

        // Info klip (kiri bawah).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 88.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.username?.takeIf { it.isNotBlank() } ?: "Anonim",
                color = ZenimePrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            item.caption?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (animeId.isNotBlank() && !item.anime.isNullOrBlank()) {
                ActionChip(text = item.anime, highlighted = true) { onAnimeClick(animeId) }
            }
            if (episodeId.isNotBlank()) {
                val label = item.episode?.takeIf { it.isNotBlank() } ?: "Tonton episode"
                ActionChip(text = "$label (tonton lengkap)") { onEpisodeClick(episodeId, animeId) }
            }
        }

        // Statistik (kanan bawah), hanya baca.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatItem(formatCount(item.countLikes), "suka")
            StatItem(formatCount(item.countComments), "komentar")
            StatItem(formatCount(item.countViews), "tayangan")
        }
    }
}

@Composable
private fun CuplixEmptyState(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(
                color = ZenimePrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error ?: "Belum ada Cuplix.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActionChip(text = "Coba lagi", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) ZenimePrimary else Color.Black.copy(alpha = 0.45f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ActionChip(
    text: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (highlighted) ZenimePrimary else Color.White.copy(alpha = 0.25f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatCount(raw: String?): String {
    val n = raw?.toLongOrNull() ?: return "0"
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fjt", n / 1_000_000.0).replace(".0", "")
        n >= 1_000 -> String.format(Locale.US, "%.1frb", n / 1_000.0).replace(".0", "")
        else -> n.toString()
    }
}
