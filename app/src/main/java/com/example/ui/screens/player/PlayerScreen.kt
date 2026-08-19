package com.example.ui.screens.player

import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.AdManager
import com.example.data.common.Result
import com.example.data.model.EpisodeItem
import com.example.ui.components.ErrorStateView
import com.example.util.PipController
import com.example.util.findActivity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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

    // Orientasi landscape, immersive mode (sembunyiin status/nav bar), dan
    // keep-screen-on buat PlayerScreen SEMUANYA di-lock di level NavGraph
    // (berdasarkan current route), BUKAN di dalam PlayerScreen. Kalau
    // ditaruh per-composable (DisposableEffect di dalam PlayerScreen),
    // transisi "Episode Selanjutnya" (dispose PlayerScreen lama + compose
    // yang baru, sempat overlap pas animasi) bikin race: onDispose
    // PlayerScreen LAMA kejalan SETELAH PlayerScreen BARU sempet nge-set
    // immersive, jadi malah nampilin balik status bar & nav bar sistem di
    // episode barunya. Lihat NavGraph.kt buat implementasi yang bener.

    // Daftarin ke PipController selama PlayerScreen ini hidup, biar
    // MainActivity tau boleh auto-masuk PiP pas user pindah app. Dilepas
    // lagi pas keluar dari player.
    DisposableEffect(Unit) {
        PipController.setCanEnterPip(true)
        onDispose {
            PipController.setCanEnterPip(false)
        }
    }

    // Tampilin video ad (interstitial, bisa di-skip) sekali tiap kali layar
    // ini dibuka buat episode baru. PlayerScreen di-compose ulang dari nol
    // tiap pindah episode (lihat NavGraph: PlayerViewModel baru dibikin per
    // episodeId), jadi LaunchedEffect(Unit) di sini otomatis cuma jalan
    // sekali per episode -- BUKAN tiap kali user pencet tombol play.
    LaunchedEffect(Unit) {
        val activity = context.findActivity()
        if (activity != null) {
            AdManager.showInterstitial(activity) {}
        }
    }

    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val resumePositionMs by viewModel.resumePositionMs.collectAsStateWithLifecycle()
    val autoSkipIntro by viewModel.autoSkipIntro.collectAsStateWithLifecycle()
    val autoSkipOutro by viewModel.autoSkipOutro.collectAsStateWithLifecycle()
    val episodeListState by viewModel.episodeListState.collectAsStateWithLifecycle()

    var showEpisodeList by remember { mutableStateOf(false) }

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

    // Kualitas & kecepatan digabung jadi satu menu "Settings" (satu ikon),
    // gaya app streaming modern -- daripada dua ikon terpisah yang bikin
    // top bar penuh.
    var showSettingsMenu by remember { mutableStateOf(false) }

    val isInPip by PipController.isInPipMode.collectAsState()

    // Gesture kontrol: swipe vertikal kiri = brightness, kanan = volume;
    // double-tap kiri/kanan = mundur/maju 10 detik. State di bawah cuma
    // buat nge-drive tampilan indikator/flash-nya.
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember { (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1) }

    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    var seekFlashTrigger by remember { mutableIntStateOf(0) }
    var seekFlashIsForward by remember { mutableStateOf(true) }
    var showSeekFlash by remember { mutableStateOf(false) }

    // Baca level brightness & volume SAAT INI sekali di awal, biar drag
    // pertama mulai dari posisi yang bener (bukan ujug-ujug dari 50%).
    LaunchedEffect(Unit) {
        val activity = context.findActivity()
        val windowBrightness = activity?.window?.attributes?.screenBrightness
        brightnessLevel = if (windowBrightness != null && windowBrightness >= 0f) {
            windowBrightness
        } else {
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            }.getOrDefault(0.5f)
        }.coerceIn(0f, 1f)

        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVolume / 2)
        volumeLevel = (currentVol.toFloat() / maxVolume).coerceIn(0f, 1f)
    }

    // Sembunyiin indikator brightness/volume ~600ms setelah jari diangkat,
    // bukan langsung ilang -- biar sempat kebaca angka akhirnya.
    LaunchedEffect(isDraggingBrightness) {
        if (isDraggingBrightness) {
            showBrightnessIndicator = true
        } else {
            delay(600)
            showBrightnessIndicator = false
        }
    }
    LaunchedEffect(isDraggingVolume) {
        if (isDraggingVolume) {
            showVolumeIndicator = true
        } else {
            delay(600)
            showVolumeIndicator = false
        }
    }

    // Flash ikon "+10/-10" pas double-tap, ilang otomatis abis sebentar.
    LaunchedEffect(seekFlashTrigger) {
        if (seekFlashTrigger == 0) return@LaunchedEffect
        showSeekFlash = true
        delay(500)
        showSeekFlash = false
    }

    // Tutup semua dropdown/sidebar pas beneran masuk mode PiP -- jendela
    // kecil gak ada gunanya nampilin menu yang gak bisa disentuh dengan
    // nyaman, dan overlay kontrolnya sendiri disembunyiin total (lihat di
    // bawah).
    LaunchedEffect(isInPip) {
        if (isInPip) {
            showSettingsMenu = false
            showEpisodeList = false
            isControlsVisible = false
        }
    }

    fun applyBrightness(value: Float) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val params = window.attributes
        params.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = params
    }

    fun applyVolume(value: Float) {
        val am = audioManager ?: return
        val target = (value * maxVolume).roundToInt().coerceIn(0, maxVolume)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

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
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                PipController.setAspectRatio(videoSize.width, videoSize.height)
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
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { isControlsVisible = !isControlsVisible },
                                    onDoubleTap = { offset ->
                                        val isLeftSide = offset.x < size.width / 2f
                                        if (isLeftSide) {
                                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                            exoPlayer.seekTo(newPos)
                                            seekFlashIsForward = false
                                        } else {
                                            val cap = if (duration > 0) duration else Long.MAX_VALUE
                                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(cap)
                                            exoPlayer.seekTo(newPos)
                                            seekFlashIsForward = true
                                        }
                                        seekFlashTrigger++
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                var isLeftSideDrag = false
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isLeftSideDrag = offset.x < size.width / 2f
                                        if (isLeftSideDrag) {
                                            isDraggingBrightness = true
                                        } else {
                                            isDraggingVolume = true
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // Swipe ke atas nambah level -- makanya dibalik (negatif).
                                        val deltaFrac = -dragAmount.y / size.height.toFloat()
                                        if (isLeftSideDrag) {
                                            brightnessLevel = (brightnessLevel + deltaFrac).coerceIn(0f, 1f)
                                            applyBrightness(brightnessLevel)
                                        } else {
                                            volumeLevel = (volumeLevel + deltaFrac).coerceIn(0f, 1f)
                                            applyVolume(volumeLevel)
                                        }
                                    },
                                    onDragEnd = {
                                        isDraggingBrightness = false
                                        isDraggingVolume = false
                                    },
                                    onDragCancel = {
                                        isDraggingBrightness = false
                                        isDraggingVolume = false
                                    }
                                )
                            }
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

                        // Semua overlay interaktif (kontrol, indikator gesture,
                        // sidebar episode) disembunyiin total pas beneran lagi
                        // PiP -- jendela kecilnya cuma nampilin video mentah,
                        // sistem yang gambar tombol play/pause/close sendiri.
                        if (!isInPip) {
                            // Flash ikon "+10/-10" pas double-tap kiri/kanan.
                            AnimatedVisibility(
                                visible = showSeekFlash,
                                enter = fadeIn(tween(120)),
                                exit = fadeOut(tween(200)),
                                modifier = Modifier
                                    .align(if (seekFlashIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                                    .padding(horizontal = 56.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Icon(
                                        imageVector = if (seekFlashIsForward) Icons.Default.Forward10 else Icons.Default.Replay10,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .size(30.dp)
                                    )
                                }
                            }

                            // Indikator brightness pas swipe di setengah layar kiri.
                            AnimatedVisibility(
                                visible = showBrightnessIndicator,
                                enter = fadeIn(tween(100)),
                                exit = fadeOut(tween(200)),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 28.dp)
                            ) {
                                GestureLevelIndicator(
                                    icon = brightnessIconFor(brightnessLevel),
                                    level = brightnessLevel
                                )
                            }

                            // Indikator volume pas swipe di setengah layar kanan.
                            AnimatedVisibility(
                                visible = showVolumeIndicator,
                                enter = fadeIn(tween(100)),
                                exit = fadeOut(tween(200)),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 28.dp)
                            ) {
                                GestureLevelIndicator(
                                    icon = volumeIconFor(volumeLevel),
                                    level = volumeLevel
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

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        // Badge "EP N" kecil, gaya chip -- lebih enak
                                        // dipindai mata daripada teks polos nyambung
                                        // sama judul di baris yang sama.
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PlayerAccent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "EP ${epDetail?.index ?: "-"}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = PlayerAccent,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        val epTitle = epDetail?.title
                                        if (!epTitle.isNullOrEmpty()) {
                                            Text(
                                                text = epTitle,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                ),
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Picture-in-Picture Trigger -- manual, di
                                    // luar auto-PiP pas user pindah app.
                                    PlayerIconButton(
                                        icon = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        onClick = {
                                            context.findActivity()?.let { PipController.requestEnter(it) }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Episode List Sidebar Trigger
                                    PlayerIconButton(
                                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = "Daftar Episode",
                                        onClick = { showEpisodeList = true }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Settings -- kualitas & kecepatan digabung jadi
                                    // satu menu, satu ikon aja (bukan dua terpisah).
                                    Box {
                                        PlayerIconButton(
                                            icon = Icons.Default.Settings,
                                            contentDescription = "Pengaturan Video",
                                            onClick = { showSettingsMenu = true },
                                            badge = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else null
                                        )
                                        PlayerSettingsMenu(
                                            expanded = showSettingsMenu,
                                            onDismissRequest = { showSettingsMenu = false },
                                            qualityOptions = servers.map { server ->
                                                PlayerMenuOption(
                                                    label = "${server.name ?: "Server"} (${server.quality ?: "720p"})",
                                                    isSelected = selectedServer?.id == server.id,
                                                    onClick = {
                                                        viewModel.selectServer(server)
                                                        showSettingsMenu = false
                                                    }
                                                )
                                            },
                                            speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).map { speed ->
                                                PlayerMenuOption(
                                                    label = "${speed}x",
                                                    isSelected = playbackSpeed == speed,
                                                    onClick = {
                                                        playbackSpeed = speed
                                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                        showSettingsMenu = false
                                                    }
                                                )
                                            }
                                        )
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
                                            .size(76.dp)
                                            .shadow(
                                                elevation = 14.dp,
                                                shape = CircleShape,
                                                clip = false,
                                                ambientColor = PlayerAccent.copy(alpha = 0.5f)
                                            )
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

                                // Bottom Scrubber + Skip Intro + Next Episode
                                // Semua pill ditumpuk dalam satu Column (bukan
                                // absolute-position sendiri2) biar nggak pernah
                                // tabrakan walau kondisinya bareng.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    val nextEpId = nextEpDetail?.id
                                    val showSkipIntro = currentPosition < INTRO_SKIP_MS &&
                                        duration > MIN_DURATION_FOR_SKIP_MS
                                    val showNextEpisode = !nextEpId.isNullOrEmpty()

                                    AnimatedVisibility(
                                        visible = showSkipIntro || showNextEpisode,
                                        enter = fadeIn(tween(180)) + slideInHorizontally(
                                            animationSpec = tween(180),
                                            initialOffsetX = { it / 4 }
                                        ),
                                        exit = fadeOut(tween(140)) + slideOutHorizontally(
                                            animationSpec = tween(140),
                                            targetOffsetX = { it / 4 }
                                        )
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                // Prioritaskan satu pill aja biar minimalis:
                                                // selama intro, tombol skip intro dulu yang
                                                // tampil. Begitu intro lewat, baru tombol
                                                // episode selanjutnya muncul.
                                                when {
                                                    showSkipIntro -> PlayerPill(
                                                        text = "Lewati Intro",
                                                        icon = Icons.Default.FastForward,
                                                        onClick = { exoPlayer.seekTo(INTRO_SKIP_MS) }
                                                    )
                                                    showNextEpisode -> PlayerPill(
                                                        text = "Episode Selanjutnya",
                                                        icon = Icons.Default.SkipNext,
                                                        filled = true,
                                                        onClick = { onNextEpisodeClick(nextEpId!!) }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
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

                        // Sidebar Daftar Episode -- sengaja di luar
                        // AnimatedVisibility kontrol di atas, biar tetap
                        // kebuka walau overlay kontrol lagi auto-hide.
                        EpisodeListSidebar(
                            visible = showEpisodeList,
                            episodeListState = episodeListState,
                            currentEpisodeId = viewModel.episodeId,
                            onDismiss = { showEpisodeList = false },
                            onEpisodeClick = { ep ->
                                showEpisodeList = false
                                onNextEpisodeClick(ep.id)
                            }
                        )
                        } // tutup if (!isInPip)
                    }
                }
            }
        }
    }
}

/** Pilih ikon brightness yang paling nyambung sama level saat ini. */
private fun brightnessIconFor(level: Float): ImageVector = when {
    level < 0.15f -> Icons.Default.BrightnessLow
    level < 0.7f -> Icons.Default.BrightnessMedium
    else -> Icons.Default.BrightnessHigh
}

/** Pilih ikon volume yang paling nyambung sama level saat ini. */
private fun volumeIconFor(level: Float): ImageVector = when {
    level <= 0f -> Icons.Default.VolumeOff
    level < 0.5f -> Icons.Default.VolumeDown
    else -> Icons.Default.VolumeUp
}

/**
 * Pill vertikal buat nunjukin level brightness/volume pas lagi di-swipe --
 * ikon di atas, track tipis di tengah, persentase di bawah. Muncul di sisi
 * kiri (brightness) atau kanan (volume) layar selama jari nyentuh, ilang
 * beberapa saat setelah dilepas.
 */
@Composable
private fun GestureLevelIndicator(
    icon: ImageVector,
    level: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(PlayerAccent)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${(level * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
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

private data class PlayerMenuOption(
    val label: String,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

/**
 * Popup di Compose bikin window Android baru yang KELUAR dari window
 * activity utama -- jadi gak otomatis kewarisin flag immersive (status/nav
 * bar disembunyiin) punya window utama. Begitu popup ini ambil fokus,
 * sistem nampilin balik status & nav bar karena window yang baru fokus
 * belum pernah minta buat disembunyiin.
 *
 * Fix-nya: minta window si popup ini sendiri buat sembunyiin system bars,
 * lewat ViewCompat (bukan lewat `window` Activity, karena popup bukan
 * bagian dari situ). Dipanggil sekali tiap kali popup ke-compose.
 */
@Composable
private fun ImmersivePopupEffect() {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val insetsController = ViewCompat.getWindowInsetsController(view) ?: return@LaunchedEffect
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Judul section + list opsi -- dipakai di [PlayerSettingsMenu]. */
@Composable
private fun PlayerMenuSection(title: String, options: List<PlayerMenuOption>) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    options.forEach { option ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = option.onClick
                )
                .background(
                    if (option.isSelected) PlayerAccent.copy(alpha = 0.14f) else Color.Transparent
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (option.isSelected) PlayerAccent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            if (option.isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = PlayerAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Menu "Settings" gabungan -- kualitas video & kecepatan putar dalam satu
 * panel, dipisah garis tipis antar section. Gantiin dua ikon+dropdown
 * terpisah biar top bar lebih minimalis, mirip menu pengaturan satu pintu
 * di app streaming modern (YouTube, Netflix).
 */
@Composable
private fun PlayerSettingsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    qualityOptions: List<PlayerMenuOption>,
    speedOptions: List<PlayerMenuOption>,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val density = LocalDensity.current

    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(x = 0, y = 48.dp.roundToPx()) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        ImmersivePopupEffect()

        val visibleState = remember { MutableTransitionState(false) }
        LaunchedEffect(Unit) { visibleState.targetState = true }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f, animationSpec = tween(140)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.9f, animationSpec = tween(100))
        ) {
            Column(
                modifier = modifier
                    .widthIn(min = 200.dp, max = 260.dp)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16171C).copy(alpha = 0.97f))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 6.dp)
            ) {
                PlayerMenuSection(title = "Kualitas Video", options = qualityOptions)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                PlayerMenuSection(title = "Kecepatan Putar", options = speedOptions)
            }
        }
    }
}

/**
 * Pill "glass" buat aksi sekunder (lewati intro, episode selanjutnya) --
 * ikon di lingkaran kecil duluan baru teks, border tipis + latar semi-transparan
 * biar kesannya "frosted glass" kayak overlay di Crunchyroll/Netflix,
 * bukan kotak solid nempel gitu aja.
 */
@Composable
private fun PlayerPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) PlayerAccent else Color(0xFF1A1B20).copy(alpha = 0.72f),
        border = if (!filled) BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)) else null,
        modifier = modifier
            .shadow(
                elevation = if (filled) 10.dp else 4.dp,
                shape = RoundedCornerShape(50),
                clip = false,
                ambientColor = if (filled) PlayerAccent.copy(alpha = 0.4f) else Color.Black
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 18.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (filled) Color.Black.copy(alpha = 0.15f) else PlayerAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (filled) Color.Black else PlayerAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = if (filled) Color.Black else Color.White
            )
        }
    }
}

/**
 * Sidebar "Daftar Episode" -- scrim gelap + panel slide-in dari kanan,
 * nampilin semua episode anime ini biar user bisa lompat episode tanpa
 * balik ke halaman detail dulu. Episode yang lagi diputar di-highlight.
 */
@Composable
private fun EpisodeListSidebar(
    visible: Boolean,
    episodeListState: Result<List<EpisodeItem>>,
    currentEpisodeId: String,
    onDismiss: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim -- tap di luar panel buat nutup
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(animationSpec = tween(220), initialOffsetX = { it }) + fadeIn(tween(220)),
                exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it }) + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .shadow(elevation = 20.dp, clip = false)
                        .background(Color(0xFF121317))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {} // Nyerap tap biar gak nembus ke scrim di belakangnya
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Episode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )

                    when (episodeListState) {
                        is Result.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = PlayerAccent,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        is Result.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Gagal memuat daftar episode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        is Result.Success -> {
                            val episodes = episodeListState.data
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(episodes, key = { it.id }) { ep ->
                                    EpisodeListRow(
                                        episode = ep,
                                        isActive = ep.id == currentEpisodeId,
                                        onClick = { onEpisodeClick(ep) }
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

/** Satu baris episode di dalam sidebar. */
@Composable
private fun EpisodeListRow(
    episode: EpisodeItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isActive) PlayerAccent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.resolvedImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = PlayerAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Episode ${episode.index ?: ""}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isActive) PlayerAccent else Color.White,
                maxLines = 1
            )
            val epTitle = episode.title
            if (!epTitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = epTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
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
