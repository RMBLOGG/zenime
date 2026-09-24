package com.example.ui.screens.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.ui.theme.ZenimePrimary
import com.example.util.MiniPlayerController
import kotlinx.coroutines.delay

/**
 * Jendela kecil ngambang di atas layar lain (Home, Search, dll) yang muncul
 * begitu ada sesi video "diminimize" dari PlayerScreen -- lihat
 * [MiniPlayerController]. Video-nya TETAP hidup (audio+gambar jalan terus),
 * cuma jendelanya kecil. Tap buat balik ke PlayerScreen penuh, tombol X buat
 * nutup & stop videonya beneran.
 *
 * @param onExpandClick dipanggil pas user tap bar-nya (bukan tombol play/X)
 * -- caller (NavGraph) yang navigate balik ke route Player buat episode ini.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerBar(
    onExpandClick: (episodeId: String, animeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by MiniPlayerController.state.collectAsState()
    val current = state ?: return

    var isPlaying by remember(current.exoPlayer) { mutableStateOf(current.exoPlayer.isPlaying) }

    // Polling simpel tiap 500ms buat nyinkronin ikon play/pause -- pola yang
    // sama kayak progress bar di PlayerScreen, gak perlu listener terpisah
    // buat komponen sekecil ini.
    LaunchedEffect(current.exoPlayer) {
        while (true) {
            isPlaying = current.exoPlayer.isPlaying
            delay(500)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onExpandClick(current.episodeId, current.animeId) },
        color = Color(0xFF1A1A2E),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail video kecil -- PlayerView yang nempel ke ExoPlayer
            // yang SAMA kayak yang tadi jalan di PlayerScreen (bukan gambar
            // statis), jadi videonya keliatan beneran lagi jalan.
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = current.animeTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.episodeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = {
                if (current.exoPlayer.isPlaying) current.exoPlayer.pause() else current.exoPlayer.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = ZenimePrimary
                )
            }

            IconButton(onClick = { MiniPlayerController.close() }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Tutup Mini Player",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
