package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.util.MiniPlayerManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Rasio lebar:tinggi kartu (dari ukuran default 190x120) -- dipertahanin
// selama resize biar video-nya gak gepeng/molor.
private const val ASPECT_RATIO = 190f / 120f
private val DEFAULT_WIDTH = 190.dp
private val MIN_WIDTH = 130.dp

/**
 * Kartu mini player ngambang, bisa digeser ke mana aja DAN diresize (tarik
 * handle di pojok kanan-bawah). Ngerender video ASLI (PlayerView nempel ke
 * ExoPlayer yang sama dari MiniPlayerManager) -- bukan cuma thumbnail
 * statis.
 *
 * Tap tipis di badan kartu (gak digeser) = balik ke PlayerScreen penuh.
 * Geser badan kartu = pindah posisi. Tarik handle pojok = resize. Tombol
 * play/pause & X punya area klik sendiri.
 */
@Composable
fun MiniPlayerOverlay(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info by MiniPlayerManager.info.collectAsState()
    val isPlaying by MiniPlayerManager.isPlaying.collectAsState()
    val player = MiniPlayerManager.player
    val currentInfo = info ?: return

    val density = LocalDensity.current
    val tapSlopPx = with(density) { 8.dp.toPx() }

    // Tombol play/pause gak boleh nempel terus di layar (nutupin subtitle)
    // -- cuma muncul sesaat abis mini player baru dibuka, atau abis kartu
    // di-tap, terus ilang sendiri.
    var controlsVisible by remember(currentInfo.episodeId) { mutableStateOf(true) }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(1800)
            controlsVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthAllowedDp = (maxWidth * 0.85f)
        val maxWidthAllowedPx = with(density) { maxWidthAllowedDp.toPx() }
        val minWidthPx = with(density) { MIN_WIDTH.toPx() }

        // Lebar kartu dalam px (state utama) -- tinggi selalu ngikut
        // ASPECT_RATIO dari ini, biar video gak distorsi.
        var cardWidthPx by remember(currentInfo.episodeId) {
            mutableFloatStateOf(with(density) { DEFAULT_WIDTH.toPx() })
        }
        val cardHeightPx = cardWidthPx / ASPECT_RATIO
        val cardWidthDp: Dp = with(density) { cardWidthPx.toDp() }
        val cardHeightDp: Dp = with(density) { cardHeightPx.toDp() }

        val maxXPx = with(density) { maxWidth.toPx() } - cardWidthPx
        val maxYPx = with(density) { maxHeight.toPx() } - cardHeightPx

        var offset by remember(currentInfo.episodeId) {
            mutableStateOf(
                Offset(
                    x = (with(density) { maxWidth.toPx() } - cardWidthPx).coerceAtLeast(0f),
                    y = with(density) { 96.dp.toPx() }
                )
            )
        }

        // Kalau ukuran kartu berubah (abis resize) dan itu bikin posisinya
        // "kedorong" keluar layar, tarik balik ke dalam batas yang valid.
        LaunchedEffect(cardWidthPx, maxXPx, maxYPx) {
            offset = Offset(
                x = offset.x.coerceIn(0f, maxXPx.coerceAtLeast(0f)),
                y = offset.y.coerceIn(0f, maxYPx.coerceAtLeast(0f))
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .width(cardWidthDp)
                .height(cardHeightDp)
                .pointerInput(currentInfo.episodeId) {
                    var totalDrag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { totalDrag = Offset.Zero },
                        onDragEnd = {
                            if (totalDrag.getDistance() < tapSlopPx) {
                                onExpand()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            offset = Offset(
                                x = (offset.x + dragAmount.x).coerceIn(0f, maxXPx.coerceAtLeast(0f)),
                                y = (offset.y + dragAmount.y).coerceIn(0f, maxYPx.coerceAtLeast(0f))
                            )
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                // Video asli (nyambung ke ExoPlayer yang sama); fallback ke
                // poster statis kalau player-nya entah kenapa udah null.
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.player = player
                            }
                        },
                        update = { view -> view.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!currentInfo.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = currentInfo.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Tombol play/pause tengah -- fade in/out, gak nempel terus
                val controlsAlpha by animateFloatAsState(
                    targetValue = if (controlsVisible) 1f else 0f,
                    label = "controlsAlpha"
                )
                if (controlsAlpha > 0f) {
                    IconButton(
                        onClick = {
                            MiniPlayerManager.togglePlayPause()
                            controlsVisible = true
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(controlsAlpha)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.35f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                }

                // Tombol tutup
                IconButton(
                    onClick = { MiniPlayerManager.release() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup mini player",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Judul/episode SENGAJA gak dirender di sini lagi -- nutupin
                // subtitle video (lihat request user). Info judul/episode
                // masih ada di MiniPlayerManager.info kalau nanti perlu
                // ditampilin di tempat lain (misal notifikasi).


                // Handle resize -- pojok kanan-bawah. Tarik buat gedein,
                // dorong balik buat ngecilin. Icon-nya sengaja diputer 90
                // derajat biar ujung panahnya ngarah diagonal (⤡).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .pointerInput(currentInfo.episodeId) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Rata-rata dua sumbu drag biar kerasa natural
                                // ditarik dari sudut mana pun (kanan/bawah).
                                val delta = (dragAmount.x + dragAmount.y) / 2f
                                cardWidthPx = (cardWidthPx + delta)
                                    .coerceIn(minWidthPx, maxWidthAllowedPx)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Ubah ukuran mini player",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(45f)
                    )
                }
            }
        }
    }
}
