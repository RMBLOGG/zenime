package com.example.ui.screens.donghua

import android.net.Uri
import androidx.annotation.OptIn as UnstableOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.api.AnichinNetwork
import com.example.data.common.Result
import com.example.data.local.PremiumStatusCache
import com.example.data.model.AnichinMedia
import com.example.data.repository.AnichinRepository
import com.example.data.repository.PremiumRepository
import com.example.ui.components.ErrorStateView
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary
import com.example.util.friendlyErrorMessage

@UnstableOptIn(UnstableApi::class)
@Composable
fun DonghuaPlayerScreen(
    viewModel: DonghuaPlayerViewModel,
    firebaseUid: String?,
    onBackClick: () -> Unit,
    onEpisodeChange: (episodeSlug: String) -> Unit,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val episodeState by viewModel.episodeState.collectAsStateWithLifecycle()

    // Status premium: cek live, kalau gagal pakai cache offline, kalau gak ada = non-premium.
    // Sama persis logikanya kayak PremiumGate di player anime.
    var isPremium by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(firebaseUid) {
        val cache = PremiumStatusCache(context)
        val res = if (firebaseUid.isNullOrBlank()) null
        else PremiumRepository(cache).checkPremiumStatus(firebaseUid)
        isPremium = if (res != null && res.isSuccess) {
            res.getOrNull()?.isPremium ?: false
        } else {
            cache.getValidOfflineStatus() ?: false
        }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Video gagal diputar (${error.errorCodeName}). Coba kualitas lain."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Jeda otomatis pas app ke background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val premium = isPremium
    val medias: List<AnichinMedia> = (videoState as? Result.Success)?.data?.medias.orEmpty()
        .filter { !it.url.isNullOrBlank() && AnichinRepository.qualityRank(it.quality) >= 0 }
        .sortedBy { AnichinRepository.qualityRank(it.quality) }

    // Kualitas terpilih: default = tertinggi yang boleh (non-premium max 480p)
    var selectedQuality by remember(medias.size, premium) {
        mutableStateOf(
            premium?.let {
                AnichinRepository.pickMedia(
                    medias,
                    if (it) "ultra" else AnichinRepository.NON_PREMIUM_MAX_QUALITY
                )?.quality
            }
        )
    }
    val selectedMedia = medias.firstOrNull { it.quality == selectedQuality }

    LaunchedEffect(selectedMedia?.url) {
        val url = selectedMedia?.url ?: return@LaunchedEffect
        playbackError = null
        val resumeAt = exoPlayer.currentPosition
        val source = ProgressiveMediaSource.Factory(AnichinNetwork.videoDataSourceFactory())
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        if (resumeAt > 0) exoPlayer.seekTo(resumeAt)
        exoPlayer.playWhenReady = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- Area video ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .statusBarsPadding()
                .aspectRatio(16f / 9f)
        ) {
            when {
                videoState is Result.Error -> {
                    ErrorStateView(
                        message = friendlyErrorMessage(
                            (videoState as Result.Error).exception,
                            "Gagal mengambil link video."
                        ),
                        onRetry = { viewModel.loadVideo() }
                    )
                }
                videoState is Result.Success && medias.isEmpty() -> {
                    Text(
                        text = "Video tidak tersedia untuk episode ini.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                videoState is Result.Loading || premium == null -> {
                    CircularProgressIndicator(color = ZenimePrimary, modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        }

        // ---- Info + kontrol ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val episodeDetail = (episodeState as? Result.Success)?.data
            val title = episodeDetail?.name
                ?: (videoState as? Result.Success)?.data?.title
                ?: "Memutar episode..."
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            playbackError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ZenimePrimary)
            }

            if (medias.isNotEmpty() && premium != null) {
                Text(
                    text = "Kualitas",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(medias, key = { it.quality ?: it.url.orEmpty() }) { m ->
                        val locked = !premium &&
                            AnichinRepository.qualityRank(m.quality) >
                            AnichinRepository.qualityRank(AnichinRepository.NON_PREMIUM_MAX_QUALITY)
                        val selected = m.quality == selectedQuality
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) ZenimePrimary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .then(
                                    if (!selected) Modifier.border(1.dp, CardOutlineBorder, RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .clickable {
                                    if (locked) onUpgradeClick() else selectedQuality = m.quality
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = AnichinRepository.qualityLabel(m.quality),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (locked) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Khusus Premium",
                                        tint = ZenimePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sebelumnya / Berikutnya: daftar di situs sumber urut terbaru -> terlama,
            // jadi episode BERIKUTNYA = index - 1, SEBELUMNYA = index + 1.
            val episodes = episodeDetail?.episodes.orEmpty()
            val currentIdx = episodes.indexOfFirst { it.slug == episodeSlugOf(viewModel) }
            if (episodes.isNotEmpty() && currentIdx >= 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onEpisodeChange(episodes[currentIdx + 1].slug) },
                        enabled = currentIdx + 1 < episodes.size,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sebelumnya") }
                    OutlinedButton(
                        onClick = { onEpisodeChange(episodes[currentIdx - 1].slug) },
                        enabled = currentIdx - 1 >= 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Berikutnya") }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun episodeSlugOf(vm: DonghuaPlayerViewModel): String = vm.slug
