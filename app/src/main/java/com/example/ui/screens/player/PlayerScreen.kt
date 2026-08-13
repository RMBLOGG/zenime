package com.example.ui.screens.player

import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.example.ui.components.ErrorStateView
import com.example.util.findActivity
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

// Durasi intro yang dilompatin pas auto-skip (dalam ms). Nggak ada timestamp
// intro/outro asli dari API, jadi dipakai perkiraan tetap kayak kebanyakan
// app nonton anime lain.
private const val INTRO_SKIP_MS = 90_000L
// Berapa lama sebelum episode abis dianggap "zona outro" buat auto-lanjut.
private const val OUTRO_WINDOW_MS = 85_000L
// Episode harus minimal sepanjang ini biar auto-skip intro/outro jalan
// (biar OVA/klip pendek nggak ke-skip abis).
private const val MIN_DURATION_FOR_SKIP_MS = INTRO_SKIP_MS * 3

// Warna aksen player. Dipisah dari MaterialTheme.colorScheme.primary supaya
// kontrol player konsisten di atas video apa pun (gak ikut ganti-ganti kalau
// tema app diubah), sama kayak kebanyakan app streaming yang punya identitas
// warna sendiri buat player-nya.
private val PlayerAccent = Color(0xFF4DD8FF)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onNextEpisodeClick: (nextEpId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Orientasi landscape buat PlayerScreen di-lock di level NavGraph
    // (berdasarkan current route), BUKAN di sini. Kalau di-lock per-composable
    // kayak sebelumnya, transisi "Episode Selanjutnya" (dispose PlayerScreen
    // lama + compose yang baru, sempat overlap pas animasi) bikin race:
    // onDispose PlayerScreen lama kejalan SETELAH PlayerScreen baru sempet
    // nge-set landscape, jadi malah balik ke portrait. Lihat NavGraph.kt.

    // Layar jangan sampe mati/kekunci sendiri selama nonton, meskipun gak ada
    // sentuhan ke layar (nonton anime kan biasanya cuma diliatin, gak dipegang terus).
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Sembunyikan status bar & navigation bar (immersive) selama nonton,
        // biar gak ada bar sistem yang nongol di atas/samping video.
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Balikin status bar & navigation bar pas keluar dari PlayerScreen.
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
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
    var isBuffering by remember { mutableStateOf(false) }
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
                isBuffering = playbackState == Player.STATE_BUFFERING
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
                        CircularProgressIndicator(color = PlayerAccent)
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
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { isControlsVisible = !isControlsVisible }
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

                        // Buffering spinner -- selalu tampil terlepas dari overlay
                        // kontrol, biar user tetep tau player lagi loading walau
                        // kontrolnya lagi disembunyiin.
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = PlayerAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp)
                            )
                        }

                        // Custom Controls Overlay
                        AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Scrim gradient atas -- gelap di tepi paling atas,
                                // memudar ke transparan. Bukan overlay hitam rata di
                                // seluruh layar, biar video tetep keliatan jernih di
                                // area tengah kayak Crunchyroll/Netflix.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.75f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Scrim gradient bawah
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )

                                // Top Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlayerIconButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Kembali",
                                        onClick = onBackClick,
                                        modifier = Modifier.testTag("player_back_button")
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Episode ${epDetail?.index ?: ""}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        val epTitle = epDetail?.title
                                        if (!epTitle.isNullOrEmpty()) {
                                            Text(
                                                text = epTitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Quality Picker Icon
                                    Box {
                                        PlayerIconButton(
                                            icon = Icons.Default.HighQuality,
                                            contentDescription = "Pilih Kualitas",
                                            onClick = { showQualityMenu = true }
                                        )
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

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Playback Speed Menu
                                    Box {
                                        PlayerIconButton(
                                            icon = Icons.Default.Speed,
                                            contentDescription = "Kecepatan Putar",
                                            onClick = { showSpeedMenu = true },
                                            badge = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else null
                                        )
                                        DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false }
                                        ) {
                                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "${speed}x",
                                                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
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
                                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlayerIconButton(
                                        icon = Icons.Default.Replay10,
                                        contentDescription = "Mundur 10 Detik",
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                            exoPlayer.seekTo(newPos)
                                        },
                                        size = 46.dp,
                                        iconSize = 26.dp
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                if (exoPlayer.isPlaying) {
                                                    exoPlayer.pause()
                                                } else {
                                                    exoPlayer.play()
                                                }
                                            }
                                            .testTag("play_pause_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.Black,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }

                                    PlayerIconButton(
                                        icon = Icons.Default.Forward10,
                                        contentDescription = "Maju 10 Detik",
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
                                            exoPlayer.seekTo(newPos)
                                        },
                                        size = 46.dp,
                                        iconSize = 26.dp
                                    )
                                }

                                // Manual "Lewati Intro" pill — tetep muncul walau
                                // auto-skip nyala/mati, buat jaga-jaga kalau lompatan
                                // otomatisnya kurang pas.
                                if (currentPosition < INTRO_SKIP_MS && duration > MIN_DURATION_FOR_SKIP_MS) {
                                    PlayerPill(
                                        text = "Lewati Intro",
                                        icon = Icons.Default.FastForward,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 84.dp, end = 16.dp),
                                        onClick = { exoPlayer.seekTo(INTRO_SKIP_MS) }
                                    )
                                }

                                // Bottom Scrubber + Next Episode Row
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    val nextEpId = nextEpDetail?.id
                                    if (!nextEpId.isNullOrEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            PlayerPill(
                                                text = "Episode Selanjutnya",
                                                icon = Icons.Default.SkipNext,
                                                filled = true,
                                                onClick = { onNextEpisodeClick(nextEpId) }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    VideoProgressBar(
                                        progressFraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                        accentColor = PlayerAccent,
                                        onSeek = { frac ->
                                            val targetMs = (frac * duration).roundToLong()
                                            exoPlayer.seekTo(targetMs)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatTime(currentPosition),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color.White
                                        )
                                        Text(
                                            text = formatTime(duration),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color.White.copy(alpha = 0.7f)
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
}

/**
 * Tombol ikon standar buat toolbar player: lingkaran kecil dengan background
 * semi-transparan biar ikon putih tetep kebaca di atas frame video seterang
 * apa pun (masalah utama di desain lama, ikon polos gampang ilang di frame terang).
 */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    badge: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Pill kecil buat aksi sekunder (lewati intro, episode selanjutnya). */
@Composable
private fun PlayerPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (filled) PlayerAccent else Color.Black.copy(alpha = 0.55f),
        modifier = modifier
            .shadow(elevation = if (filled) 6.dp else 0.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (filled) Color.Black else Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (filled) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Progress bar custom yang digambar sendiri (bukan Material3 Slider bawaan),
 * biar bentuknya persis kayak player streaming profesional: track tipis,
 * ujung membulat, thumb kecil yang membesar pas lagi di-drag.
 */
@Composable
private fun VideoProgressBar(
    progressFraction: Float,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayedFraction = (if (isDragging) dragFraction else progressFraction).coerceIn(0f, 1f)
    val thumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 8f else 5f,
        label = "thumbRadius"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragFraction)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        val trackHeight = with(density) { 3.dp.toPx() }
        val centerY = this.size.height / 2f
        val trackWidth = this.size.width

        // Track belum ke-play
        drawRoundRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Track udah ke-play
        val playedWidth = trackWidth * displayedFraction
        if (playedWidth > 0f) {
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(playedWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }

        // Thumb
        drawCircle(
            color = Color.White,
            radius = with(density) { thumbRadius.dp.toPx() },
            center = Offset(playedWidth, centerY)
        )
        drawCircle(
            color = accentColor,
            radius = with(density) { thumbRadius.dp.toPx() },
            center = Offset(playedWidth, centerY),
            style = Stroke(width = with(density) { 1.5.dp.toPx() })
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
