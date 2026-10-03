package com.example.ui.screens.donghua

import com.example.ui.screens.comments.EpisodeCommentThreads
import com.example.ui.screens.comments.episodeCommentsSection
import com.example.ui.screens.comments.commentsSectionItemCount
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as UnstableOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.AnichinNetwork
import com.example.data.common.Result
import com.example.data.local.PremiumStatusCache
import com.example.util.PlaybackCoordinator
import com.example.util.WatchXpGate
import com.example.data.model.AnichinEpisodeDetail
import com.example.data.model.AnichinEpisodeRef
import com.example.data.model.AnichinMedia
import com.example.data.repository.AnichinRepository
import com.example.data.repository.PremiumRepository
import com.example.ui.components.ErrorStateView
import com.example.ui.screens.comments.CommentsViewModel
import com.example.ui.screens.player.GestureLevelIndicator
import com.example.ui.screens.player.PlayerAccent
import com.example.ui.screens.player.PlayerActionChip
import com.example.ui.screens.player.PlayerIconButton
import com.example.ui.screens.player.PlayerMenuOption
import com.example.ui.screens.player.PlayerSettingsMenu
import com.example.ui.screens.player.QualityPickerSheet
import com.example.ui.screens.player.TrakteerDonationButton
import com.example.ui.screens.player.VideoProgressBar
import com.example.ui.screens.player.brightnessIconFor
import com.example.ui.screens.player.formatTime
import com.example.ui.screens.player.qualityAccentColor
import com.example.ui.screens.player.qualityDescription
import com.example.ui.screens.player.qualityTag
import com.example.ui.screens.player.volumeIconFor
import com.example.ui.theme.ZenimePrimary
import com.example.util.PipController
import com.example.util.PlayerFullscreenController
import com.example.util.findActivity
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.delay
import retrofit2.HttpException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Ambil "Episode 16" dari judul "Judul Anime Episode 16 Subtitle Indonesia".
private val EP_TITLE_REGEX = Regex("""\s*[-–]?\s*Episode\s*(\d+).*$""", RegexOption.IGNORE_CASE)

private fun animeTitleOf(episodeName: String?): String? =
    episodeName?.replace(EP_TITLE_REGEX, "")?.trim()?.takeIf { it.isNotBlank() }

private fun episodeNumberOf(episodeName: String?, ref: AnichinEpisodeRef?): String? =
    ref?.episode?.takeIf { it.isNotBlank() }
        ?: EP_TITLE_REGEX.find(episodeName.orEmpty())?.groupValues?.getOrNull(1)

/**
 * Player donghua -- tampilan disamakan dengan PlayerScreen anime:
 * kontrol custom (PiP, daftar episode, pengaturan, download, fullscreen),
 * gestur (swipe brightness/volume, double-tap +-10 detik), lalu di bawah video
 * ada poster + sinopsis, banner Trakteer, chip aksi, dan daftar episode bergambar.
 *
 * Data tetap dari API Anichin. Komponen UI-nya dipakai ulang dari
 * ui/screens/player/PlayerScreen.kt (yang sekarang `internal`).
 */
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
    val episodeDetail = (episodeState as? Result.Success)?.data

    // Izinkan MainActivity auto-masuk PiP selama layar player ini hidup.
    DisposableEffect(Unit) {
        PipController.setCanEnterPip(true)
        onDispose { PipController.setCanEnterPip(false) }
    }

    // Status premium: cek live, kalau gagal pakai cache offline, kalau gak ada = non-premium.
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
    val premium = isPremium

    val isInPip by PipController.isInPipMode.collectAsState()
    val isFullscreen by PlayerFullscreenController.isFullscreen.collectAsState()

    // Tombol back device: kalau lagi fullscreen, keluar fullscreen dulu.
    BackHandler(enabled = isFullscreen) {
        PlayerFullscreenController.setFullscreen(false)
    }

    // Auto-masuk fullscreen begitu device di-rotate ke landscape.
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
            PlayerFullscreenController.setFullscreen(true)
        }
    }

    // ---- State player ----
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    var showSettingsMenu by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }

    // ---- State gestur ----
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember {
        (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var seekFlashTrigger by remember { mutableIntStateOf(0) }
    var seekFlashIsForward by remember { mutableStateOf(true) }
    var showSeekFlash by remember { mutableStateOf(false) }

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
    LaunchedEffect(seekFlashTrigger) {
        if (seekFlashTrigger == 0) return@LaunchedEffect
        showSeekFlash = true
        delay(500)
        showSeekFlash = false
    }
    LaunchedEffect(isInPip) {
        if (isInPip) {
            showSettingsMenu = false
            showQualitySheet = false
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

    // ---- ExoPlayer ----
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(com.example.util.PlayerConfig.loadControl())
            .build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Video gagal diputar (${error.errorCodeName}). Coba kualitas lain."
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                PipController.setAspectRatio(videoSize.width, videoSize.height)
            }
        }
        PlaybackCoordinator.attach(exoPlayer)
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            PlaybackCoordinator.detach(exoPlayer)
            WatchXpGate.release(exoPlayer)
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

    // Update posisi + durasi
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            delay(500)
        }
    }

    // XP nonton: kirim heartbeat tiap 60 detik video BENERAN playing (pause/buffering
    // gak dihitung). Key cuma exoPlayer supaya pause/buffer sekejap gak me-reset hitungan.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1000)
            // Hitungan XP global (bukan per player) -- lihat WatchXpGate.
            if (exoPlayer.isPlaying && WatchXpGate.onActiveSecond(exoPlayer)) {
                viewModel.sendWatchHeartbeat(minutes = 1)
            }
        }
    }

    // Auto-hide kontrol
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // ---- Daftar kualitas ----
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

    fun isLocked(quality: String?): Boolean =
        premium == false &&
            AnichinRepository.qualityRank(quality) >
            AnichinRepository.qualityRank(AnichinRepository.NON_PREMIUM_MAX_QUALITY)

    LaunchedEffect(selectedMedia?.url) {
        val url = selectedMedia?.url ?: return@LaunchedEffect
        playbackError = null
        val resumeAt = exoPlayer.currentPosition
        val source = ProgressiveMediaSource.Factory(
            com.example.util.PlayerConfig.applyTimeouts(AnichinNetwork.videoDataSourceFactory())
        ).setLoadErrorHandlingPolicy(com.example.util.PlayerConfig.errorPolicy())
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        if (resumeAt > 0) exoPlayer.seekTo(resumeAt)
        exoPlayer.playWhenReady = true
    }

    // ---- Info episode ----
    // Daftar di situs sumber urut terbaru -> terlama, jadi BERIKUTNYA = index - 1,
    // SEBELUMNYA = index + 1.
    val episodes = episodeDetail?.episodes.orEmpty()
    val currentIdx = episodes.indexOfFirst { it.slug == viewModel.slug }
    val currentRef = if (currentIdx >= 0) episodes[currentIdx] else null
    val prevSlug = if (currentIdx >= 0) episodes.getOrNull(currentIdx + 1)?.slug else null
    val nextSlug = if (currentIdx > 0) episodes[currentIdx - 1].slug else null

    val fullName = episodeDetail?.name ?: (videoState as? Result.Success)?.data?.title
    val animeTitle = animeTitleOf(fullName) ?: fullName
    val epNumber = episodeNumberOf(fullName, currentRef)

    val videoReady = videoState is Result.Success && medias.isNotEmpty() && premium != null

    val downloadSoon: () -> Unit = {
        if (premium == false) {
            onUpgradeClick()
        } else {
            Toast.makeText(context, "Download donghua segera hadir", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- Area video ----
        Box(
            modifier = (
                if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
                )
                .background(Color.Black)
                .then(
                    if (videoReady) {
                        Modifier
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
                                            val cap = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
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
                                        if (isLeftSideDrag) isDraggingBrightness = true else isDraggingVolume = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
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
                    } else {
                        Modifier
                    }
                )
        ) {
            when {
                // 404 dari API = episode ini gak punya mirror OK.ru (bukan masalah koneksi)
                videoState is Result.Error &&
                    ((videoState as Result.Error).exception as? HttpException)?.code() == 404 -> {
                    Text(
                        text = "Video episode ini belum tersedia di sumber. Coba episode lain.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
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
                !videoReady -> {
                    CircularProgressIndicator(
                        color = PlayerAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // kontrol custom (Compose) di bawah
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = PlayerAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(44.dp)
                        )
                    }

                    if (!isInPip) {
                        // Flash "+10/-10" pas double-tap
                        this@Column.AnimatedVisibility(
                            visible = showSeekFlash,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier
                                .align(if (seekFlashIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(horizontal = 56.dp)
                        ) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
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

                        // Indikator brightness (kiri)
                        this@Column.AnimatedVisibility(
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

                        // Indikator volume (kanan)
                        this@Column.AnimatedVisibility(
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

                        // ---- Overlay kontrol custom ----
                        this@Column.AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Scrim atas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                            )
                                        )
                                )
                                // Scrim bawah
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                )

                                // Top bar
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
                                        modifier = Modifier.testTag("donghua_player_back_button")
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PlayerAccent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "EP ${epNumber ?: "-"}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = PlayerAccent,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (!animeTitle.isNullOrEmpty()) {
                                            Text(
                                                text = animeTitle,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                ),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    PlayerIconButton(
                                        icon = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        onClick = {
                                            context.findActivity()?.let { PipController.requestEnter(it) }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    PlayerIconButton(
                                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = "Daftar Episode",
                                        onClick = { showEpisodeList = true }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

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
                                            qualityOptions = medias.map { m ->
                                                val locked = isLocked(m.quality)
                                                PlayerMenuOption(
                                                    label = AnichinRepository.qualityLabel(m.quality),
                                                    isSelected = m.quality == selectedQuality,
                                                    isLocked = locked,
                                                    onClick = {
                                                        showSettingsMenu = false
                                                        if (locked) onUpgradeClick() else selectedQuality = m.quality
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

                                    Spacer(modifier = Modifier.width(6.dp))

                                    PlayerIconButton(
                                        icon = Icons.Default.DownloadForOffline,
                                        contentDescription = "Download",
                                        onClick = downloadSoon
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    PlayerIconButton(
                                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Keluar Fullscreen" else "Fullscreen",
                                        onClick = { PlayerFullscreenController.setFullscreen(!isFullscreen) }
                                    )
                                }

                                // Kontrol tengah
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!prevSlug.isNullOrEmpty()) {
                                        PlayerIconButton(
                                            icon = Icons.Default.SkipPrevious,
                                            contentDescription = "Episode Sebelumnya",
                                            onClick = { onEpisodeChange(prevSlug) },
                                            size = 46.dp,
                                            iconSize = 26.dp
                                        )
                                    }

                                    PlayerIconButton(
                                        icon = Icons.Default.Replay10,
                                        contentDescription = "Mundur 10 Detik",
                                        onClick = {
                                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
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
                                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            }
                                            .testTag("donghua_play_pause_button"),
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
                                            val cap = if (duration > 0) duration else Long.MAX_VALUE
                                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(cap))
                                        },
                                        size = 46.dp,
                                        iconSize = 26.dp
                                    )

                                    if (!nextSlug.isNullOrEmpty()) {
                                        PlayerIconButton(
                                            icon = Icons.Default.SkipNext,
                                            contentDescription = "Episode Selanjutnya",
                                            onClick = { onEpisodeChange(nextSlug) },
                                            size = 46.dp,
                                            iconSize = 26.dp
                                        )
                                    }
                                }

                                // Seekbar + waktu
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    VideoProgressBar(
                                        progressFraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                        accentColor = PlayerAccent,
                                        onSeek = { frac ->
                                            exoPlayer.seekTo((frac * duration).roundToLong())
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

                        // Sidebar daftar episode (di luar AnimatedVisibility kontrol)
                        DonghuaEpisodeSidebar(
                            visible = showEpisodeList,
                            episodes = episodes,
                            episodesLoading = episodeState is Result.Loading,
                            currentSlug = viewModel.slug,
                            onDismiss = { showEpisodeList = false },
                            onEpisodeClick = { slug ->
                                showEpisodeList = false
                                if (slug != viewModel.slug) onEpisodeChange(slug)
                            }
                        )
                    }
                }
            }

            // Tombol back saat video belum siap (loading / error)
            if (!videoReady) {
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
        }

        // ---- Info + daftar episode (cuma di portrait, bukan fullscreen / PiP) ----
        if (!isFullscreen && !isInPip) {
            // Komentar inline di bawah Episode List -- id diberi prefix "dh:" supaya
            // gak bentrok dengan id episode anime. Dibuat setelah detail episode
            // kebaca, biar judul/poster yang ditempel ke komentar sudah terisi.
            val commentsViewModel: CommentsViewModel? = if (episodeDetail != null) {
                composeViewModel<CommentsViewModel>(
                    key = "comments_dh_${viewModel.slug}",
                    factory = viewModelFactory {
                        initializer {
                            CommentsViewModel(
                                episodeId = "dh:${viewModel.slug}",
                                animeId = "dh:${episodeDetail?.root ?: viewModel.slug}",
                                animeTitle = animeTitle,
                                animePosterUrl = AnichinNetwork.imageUrl(episodeDetail?.thumbnail),
                                episodeIndex = epNumber
                            )
                        }
                    }
                )
            } else null

            DonghuaDetailsSection(
                animeTitle = animeTitle,
                posterPath = episodeDetail?.thumbnail,
                epNumber = epNumber,
                epSubtitle = currentRef?.subtitle,
                epDate = currentRef?.date,
                synopsis = episodeDetail?.sinopsis.orEmpty(),
                episodes = episodes,
                episodesLoading = episodeState is Result.Loading,
                currentSlug = viewModel.slug,
                playbackError = playbackError,
                onQualityClick = { showQualitySheet = true },
                onDownloadClick = downloadSoon,
                onEpisodeClick = { slug -> if (slug != viewModel.slug) onEpisodeChange(slug) },
                commentsViewModel = commentsViewModel,
                onUpgradeClick = onUpgradeClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom sheet "Pilihan Kualitas Video" (dari tombol Kualitas di bawah video)
        QualityPickerSheet(
            expanded = showQualitySheet,
            onDismissRequest = { showQualitySheet = false },
            qualityOptions = medias.map { m ->
                val locked = isLocked(m.quality)
                val label = AnichinRepository.qualityLabel(m.quality)
                PlayerMenuOption(
                    label = "$label (${qualityTag(label)})",
                    isSelected = m.quality == selectedQuality,
                    isLocked = locked,
                    description = qualityDescription(label),
                    accentColor = qualityAccentColor(label),
                    onClick = {
                        showQualitySheet = false
                        if (locked) onUpgradeClick() else selectedQuality = m.quality
                    }
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Bagian bawah video (portrait)
// ---------------------------------------------------------------------------

@Composable
private fun DonghuaDetailsSection(
    animeTitle: String?,
    posterPath: String?,
    epNumber: String?,
    epSubtitle: String?,
    epDate: String?,
    synopsis: String,
    episodes: List<AnichinEpisodeRef>,
    episodesLoading: Boolean,
    currentSlug: String,
    playbackError: String?,
    onQualityClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    commentsViewModel: CommentsViewModel?,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val commentsState = commentsViewModel?.uiState?.collectAsState()?.value

    // Tombol "Komentar" di baris aksi -> scroll ke awal bagian komentar.
    val scrollToComments: () -> Unit = {
        val st = commentsState
        if (st != null) {
            scope.launch {
                val idx = listState.layoutInfo.totalItemsCount - commentsSectionItemCount(st)
                listState.animateScrollToItem(idx.coerceAtLeast(0))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        // 1. Poster kecil + judul + info episode
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!posterPath.isNullOrBlank()) {
                        AsyncImage(
                            model = anichinImageRequest(posterPath),
                            contentDescription = animeTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animeTitle ?: "Memuat...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Episode ${epNumber ?: "-"}" +
                            if (!epSubtitle.isNullOrBlank()) " • $epSubtitle" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!epDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = epDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!playbackError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playbackError,
                            style = MaterialTheme.typography.labelMedium,
                            color = ZenimePrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Sinopsis (expandable)
        if (synopsis.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSynopsisExpanded) "Sembunyikan" else "Selengkapnya",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZenimePrimary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { isSynopsisExpanded = !isSynopsisExpanded }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 3. Banner Trakteer
        item {
            TrakteerDonationButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. Tombol aksi
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerActionChip(
                    icon = Icons.Default.HighQuality,
                    label = "Kualitas",
                    onClick = onQualityClick
                )
                PlayerActionChip(
                    icon = Icons.Default.DownloadForOffline,
                    label = "Download",
                    onClick = onDownloadClick
                )
                PlayerActionChip(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Komentar",
                    onClick = scrollToComments
                )
                PlayerActionChip(
                    icon = Icons.Default.Flag,
                    label = "Laporkan",
                    onClick = {
                        Toast.makeText(context, "Fitur laporkan segera hadir", Toast.LENGTH_SHORT).show()
                    }
                )
                PlayerActionChip(
                    icon = Icons.Default.Share,
                    label = "Bagikan",
                    onClick = {
                        val title = animeTitle ?: "donghua ini"
                        val shareText = if (!epNumber.isNullOrBlank()) {
                            "Nonton \"$title\" Episode $epNumber di Zenime!"
                        } else {
                            "Nonton \"$title\" di Zenime!"
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching { context.startActivity(Intent.createChooser(sendIntent, null)) }
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 5. Header "Episode List"
        item {
            Text(
                text = "Episode List",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 6. Daftar episode horizontal bergambar
        when {
            episodes.isNotEmpty() -> {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(episodes, key = { it.slug }) { ep ->
                            DonghuaEpisodeThumbCard(
                                episode = ep,
                                isActive = ep.slug == currentSlug,
                                onClick = { onEpisodeClick(ep.slug) }
                            )
                        }
                    }
                }
            }
            episodesLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PlayerAccent, modifier = Modifier.size(28.dp))
                    }
                }
            }
            else -> {
                item {
                    Text(
                        text = "Daftar episode belum tersedia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // 7. Komentar episode (inline, di bawah Episode List)
        if (commentsViewModel != null && commentsState != null) {
            episodeCommentsSection(
                state = commentsState,
                viewModel = commentsViewModel,
                onUpgradeClick = onUpgradeClick
            )
        }
    }

    // Sheet "Threads" (balasan) -- muncul saat tap Reply di salah satu komentar.
    if (commentsViewModel != null) {
        EpisodeCommentThreads(viewModel = commentsViewModel)
    }
}

@Composable
private fun DonghuaEpisodeThumbCard(
    episode: AnichinEpisodeRef,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = if (isActive) ZenimePrimary else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            if (!episode.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = anichinImageRequest(episode.thumbnail),
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
                        tint = ZenimePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "EP ${episodeNumberOf(episode.name, episode) ?: "-"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Episode ${episodeNumberOf(episode.name, episode) ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Sidebar daftar episode (di dalam area video)
// ---------------------------------------------------------------------------

@Composable
private fun DonghuaEpisodeSidebar(
    visible: Boolean,
    episodes: List<AnichinEpisodeRef>,
    episodesLoading: Boolean,
    currentSlug: String,
    onDismiss: () -> Unit,
    onEpisodeClick: (String) -> Unit,
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
                            onClick = {} // serap tap biar gak tembus ke scrim
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

                    when {
                        episodes.isNotEmpty() -> {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(episodes, key = { it.slug }) { ep ->
                                    DonghuaEpisodeRow(
                                        episode = ep,
                                        isActive = ep.slug == currentSlug,
                                        onClick = { onEpisodeClick(ep.slug) }
                                    )
                                }
                            }
                        }
                        episodesLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = PlayerAccent,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        else -> {
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
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonghuaEpisodeRow(
    episode: AnichinEpisodeRef,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .background(if (isActive) PlayerAccent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            if (!episode.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = anichinImageRequest(episode.thumbnail),
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Episode ${episodeNumberOf(episode.name, episode) ?: "-"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isActive) PlayerAccent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.date.isNullOrBlank()) {
                Text(
                    text = episode.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }
        }
    }
}

/** Request gambar dari situs sumber, dengan User-Agent + Referer browser biar gak ditolak. */
@Composable
private fun anichinImageRequest(path: String?): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(AnichinNetwork.imageUrl(path))
        .addHeader("User-Agent", AnichinNetwork.USER_AGENT)
        .addHeader("Referer", AnichinNetwork.sourceBase + "/")
        .crossfade(true)
        .build()
