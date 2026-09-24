package com.example.ui.screens.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.util.MiniPlayerController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val BOX_WIDTH = 148.dp
private val BOX_HEIGHT = 92.dp

/**
 * Kotak kecil ngambang yang bisa DIGESER BEBAS ke mana aja di layar (gaya
 * chat bubble Messenger) -- muncul begitu ada sesi video yang diminimize
 * dari PlayerScreen, lihat [MiniPlayerController]. Video-nya beneran masih
 * jalan (PlayerView nempel ke ExoPlayer yang sama, bukan gambar statis).
 * Tap singkat di badan kotak (bukan drag, bukan tombol play/pause/X) buat
 * balik ke PlayerScreen penuh.
 *
 * @param onExpandClick dipanggil pas kotaknya di-tap -- caller (NavGraph)
 * yang navigate balik ke route Player buat episode ini.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerBar(
    onExpandClick: (episodeId: String, animeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by MiniPlayerController.state.collectAsState()
    val current = state ?: return

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val boxWidthPx = with(density) { BOX_WIDTH.toPx() }
        val boxHeightPx = with(density) { BOX_HEIGHT.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        // Sisain ruang di bawah biar posisi AWALNYA gak numpuk sama
        // FloatingPillBottomBar + navigation bar sistem -- user tetap bebas
        // geser ke situ kalau emang mau.
        val bottomReservePx = with(density) { 130.dp.toPx() }

        // Posisi default: pojok kanan bawah. Key-nya episodeId -- kotaknya
        // "lupa" posisi kalau ganti ke sesi episode lain, tapi TETAP nempel
        // posisi terakhir digeser user selama masih episode yang sama.
        var offsetX by remember(current.episodeId) {
            mutableStateOf((maxWidthPx - boxWidthPx - marginPx).coerceAtLeast(0f))
        }
        var offsetY by remember(current.episodeId) {
            mutableStateOf((maxHeightPx - boxHeightPx - bottomReservePx).coerceAtLeast(0f))
        }

        var isPlaying by remember(current.exoPlayer) { mutableStateOf(current.exoPlayer.isPlaying) }
        LaunchedEffect(current.exoPlayer) {
            while (true) {
                isPlaying = current.exoPlayer.isPlaying
                delay(500)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(BOX_WIDTH, BOX_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(current.episodeId) {
                    var totalDrag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { totalDrag = Offset.Zero },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            offsetX = (offsetX + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - boxWidthPx).coerceAtLeast(0f))
                            offsetY = (offsetY + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - boxHeightPx).coerceAtLeast(0f))
                        },
                        onDragEnd = {
                            // Gerakan super kecil (di bawah 8dp) dianggap TAP,
                            // bukan drag beneran -- expand balik ke player penuh.
                            if (totalDrag.getDistance() < with(density) { 8.dp.toPx() }) {
                                onExpandClick(current.episodeId, current.animeId)
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = current.exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { it.player = current.exoPlayer },
                modifier = Modifier.fillMaxSize()
            )

            // Scrim gradient tipis di bawah biar judul kebaca di atas video
            // apa pun.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = current.animeTitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.episodeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Play/pause -- lingkaran kecil di tengah, background semi
            // transparan biar keliatan di atas video apa pun. Pakai
            // detectTapGestures (BUKAN drag) -- ini tombol beneran, gak boleh
            // ikut mindahin posisi kotak pas ditekan.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pointerInput(current.exoPlayer) {
                        detectTapGestures(onTap = {
                            if (current.exoPlayer.isPlaying) current.exoPlayer.pause() else current.exoPlayer.play()
                        })
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                )
            }

            // Tombol tutup -- pojok kanan atas, kecil biar gak makan banyak
            // ruang di kotak yang emang udah mini. Sama kayak play/pause,
            // pakai tap gesture terpisah biar gak ke-drag pas ditekan.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { MiniPlayerController.close() })
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Tutup Mini Player",
                    tint = Color.White,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}
