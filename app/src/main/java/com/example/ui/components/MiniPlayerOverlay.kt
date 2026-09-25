package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.util.MiniPlayerManager
import kotlin.math.roundToInt

/**
 * Kartu mini player ngambang, bisa digeser ke mana aja di layar. Ngerender
 * video ASLI (PlayerView nempel ke ExoPlayer yang sama dari MiniPlayerManager)
 * -- bukan cuma thumbnail statis -- biar video-nya keliatan tetep
 * jalan/pause kayak di PlayerScreen aslinya.
 *
 * Tap tipis (gak digeser) = balik ke PlayerScreen penuh. Geser = pindah
 * posisi kartu. Tombol play/pause & X punya area klik sendiri di atasnya.
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
    val cardWidth = 190.dp
    val cardHeight = 120.dp
    val tapSlopPx = with(density) { 8.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxXPx = with(density) { (maxWidth - cardWidth).toPx() }.coerceAtLeast(0f)
        val maxYPx = with(density) { (maxHeight - cardHeight).toPx() }.coerceAtLeast(0f)

        // Posisi awal: pojok kanan atas (mirip referensi), sedikit di bawah
        // header. Di-reset tiap kali episode mini player-nya ganti.
        var offset by remember(currentInfo.episodeId) {
            mutableStateOf(Offset(x = maxXPx, y = with(density) { 96.dp.toPx() }))
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .width(cardWidth)
                .height(cardHeight)
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
                                x = (offset.x + dragAmount.x).coerceIn(0f, maxXPx),
                                y = (offset.y + dragAmount.y).coerceIn(0f, maxYPx)
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

                // Tombol play/pause tengah
                IconButton(
                    onClick = { MiniPlayerManager.togglePlayPause() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.35f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
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

                // Judul + episode, gradasi gelap di bawah biar tetep kebaca
                // di atas video apa pun.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = currentInfo.animeTitle,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentInfo.episodeLabel,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
