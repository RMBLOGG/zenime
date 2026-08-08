package com.example.ui.screens.player

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.common.Result
import com.example.data.model.StreamServer
import com.example.ui.components.ErrorStateView
import kotlinx.coroutines.delay

// Durasi intro yang dilompatin pas auto-skip (dalam ms). Nggak ada timestamp
// intro/outro asli dari API, jadi dipakai perkiraan tetap kayak kebanyakan
// app nonton anime lain.
private const val INTRO_SKIP_MS = 90_000L
// Berapa lama sebelum episode abis dianggap "zona outro" buat auto-lanjut.
private const val OUTRO_WINDOW_MS = 85_000L
// Episode harus minimal sepanjang ini biar auto-skip intro/outro jalan
// (biar OVA/klip pendek nggak ke-skip abis).
private const val MIN_DURATION_FOR_SKIP_MS = INTRO_SKIP_MS * 3

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onNextEpisodeClick: (nextEpId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Auto landscape saat masuk PlayerScreen, balikin lagi pas keluar
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = originalOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Layar jangan sampe mati/kekunci sendiri selama nonton, meskipun gak ada
    // sentuhan ke layar (nonton anime kan biasanya cuma diliatin, gak dipegang terus).
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val resumePositionMs by viewModel.resumePositionMs.collectAsStateWithLifecycle()
    val autoSkipIntro by viewModel.autoSkipIntro.collectAsStateWithLifecycle()
    val autoSkipOutro by viewModel.autoSkipOutro.collectAsStateWithLifecycle()

    // Nge-track apakah seek "lanjutin dari terakhir nonton" udah pernah
    // dijalanin. Cuma sekali di awal -- ganti server/kualitas belakangan
    // gak boleh nge-reset balik ke posisi lama dari histori.
    var hasAppliedResume by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Jaga biar auto-lanjut ke episode berikutnya cuma kejadian sekali pas
    // masuk zona outro, gak nge-trigger berkali-kali tiap tick progress.
    var hasAutoSkippedOutro by remember { mutableStateOf(false) }

    // ExoPlayer instance
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://animeinweb.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            )

        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Auto-hide controls overlay
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Progress updates & periodic saving to Room
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L

                // Save progress to Room watch history every 5s
                val currentResult = streamState as? Result.Success
                val epDetail = currentResult?.data?.episode
                viewModel.saveProgress(
                    progressMs = currentPosition,
                    durationMs = duration,
                    epTitle = epDetail?.title,
                    epIndex = epDetail?.index
                )

                // Auto-skip outro: begitu masuk zona outro (mepet abis), langsung
                // lanjut ke episode berikutnya kalau ada, tanpa nunggu ditekan.
                val nextEpId = currentResult?.data?.episodeNext?.id
                if (autoSkipOutro &&
                    !hasAutoSkippedOutro &&
                    !nextEpId.isNullOrEmpty() &&
                    duration > MIN_DURATION_FOR_SKIP_MS &&
                    duration - currentPosition <= OUTRO_WINDOW_MS
                ) {
                    hasAutoSkippedOutro = true
                    onNextEpisodeClick(nextEpId)
                }
            }
            delay(1000)
        }
    }

    // Update MediaSource when selectedServer changes
    LaunchedEffect(selectedServer) {
        val serverUrl = selectedServer?.link
        if (!serverUrl.isNullOrEmpty()) {
            // Kalau ini ganti server/kualitas di TENGAH nonton (bukan load
            // pertama), jaga posisi biar gak balik ke awal. Load pertama
            // biarin 0 di sini -- posisi "lanjutin dari terakhir nonton"
            // ditangani terpisah di listener STATE_READY di bawah, soalnya
            // resumePositionMs dari histori bisa belum kebaca pas titik ini.
            val positionToKeep = if (hasAppliedResume) exoPlayer.currentPosition else 0L

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://animeinweb.com/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                )

            val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(serverUrl)))

            exoPlayer.setMediaSource(mediaSource)
            if (positionToKeep > 0) {
                exoPlayer.seekTo(positionToKeep)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // ExoPlayer listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L

                    // Sekali doang: begitu player siap pertama kali dan ada
                    // posisi tersimpan dari histori nonton, lompat ke situ.
                    // Kalau ini nonton dari awal (belum ada histori) dan
                    // auto-skip aktif, lompatin intro-nya juga.
                    if (!hasAppliedResume) {
                        if (resumePositionMs > 0) {
                            exoPlayer.seekTo(resumePositionMs)
                        } else if (autoSkipIntro && duration > MIN_DURATION_FOR_SKIP_MS) {
                            exoPlayer.seekTo(INTRO_SKIP_MS)
                        }
                        hasAppliedResume = true
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when (val state = streamState) {
                is Result.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is Result.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.loadStream() }
                    )
                }
                is Result.Success -> {
                    val streamData = state.data
                    val epDetail = streamData.episode
                    val nextEpDetail = streamData.episodeNext
                    val servers = streamData.servers ?: emptyList()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isControlsVisible = !isControlsVisible }
                    ) {
                        // Media3 Player View
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false // Custom Compose overlay controls
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Custom Controls Overlay
                        AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                // Top Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = onBackClick,
                                        modifier = Modifier.testTag("player_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Episode ${epDetail?.index ?: ""}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        val epTitle = epDetail?.title
                                        if (!epTitle.isNullOrEmpty()) {
                                            Text(
                                                text = epTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    // Quality Picker Icon
                                    Box {
                                        IconButton(onClick = { showQualityMenu = true }) {
                                            Icon(
                                                imageVector = Icons.Default.HighQuality,
                                                contentDescription = "Pilih Kualitas",
                                                tint = Color.White
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showQualityMenu,
                                            onDismissRequest = { showQualityMenu = false }
                                        ) {
                                            servers.forEach { server ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "${server.name ?: "Server"} (${server.quality ?: "720p"})",
                                                            fontWeight = if (selectedServer?.id == server.id) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.selectServer(server)
                                                        showQualityMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Playback Speed Menu
                                    Box {
                                        IconButton(onClick = { showSpeedMenu = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = "Kecepatan Putar",
                                                tint = Color.White
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false }
                                        ) {
                                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                                DropdownMenuItem(
                                                    text = { Text("${speed}x") },
                                                    onClick = {
                                                        playbackSpeed = speed
                                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                        showSpeedMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Center Play / Rewind / Forward Controls
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                            exoPlayer.seekTo(newPos)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = "Mundur 10 Detik",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                exoPlayer.play()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                                            exoPlayer.seekTo(newPos)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forward10,
                                            contentDescription = "Maju 10 Detik",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                // Manual "Lewati Intro" pill — tetep muncul walau
                                // auto-skip nyala/mati, buat jaga-jaga kalau lompatan
                                // otomatisnya kurang pas.
                                if (currentPosition < INTRO_SKIP_MS && duration > MIN_DURATION_FOR_SKIP_MS) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 72.dp, end = 16.dp)
                                            .clickable { exoPlayer.seekTo(INTRO_SKIP_MS) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Lewati Intro",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.FastForward,
                                                contentDescription = "Lewati Intro",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Bottom Scrubber + Next Episode Row
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatTime(currentPosition),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )

                                        val nextEpId = nextEpDetail?.id
                                        if (!nextEpId.isNullOrEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    onNextEpisodeClick(nextEpId)
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "Episode Selanjutnya",
                                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.SkipNext,
                                                        contentDescription = "Next Episode",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = formatTime(duration),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }

                                    Slider(
                                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                        onValueChange = { frac ->
                                            val targetMs = (frac * duration).toLong()
                                            exoPlayer.seekTo(targetMs)
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/** Cari Activity dari Context, karena LocalContext.current bisa jadi ContextWrapper (Compose). */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
