package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AnimeItem
import com.example.ui.theme.StatusCompleted
import com.example.ui.theme.StatusOngoing

@Composable
fun AnimePosterCard(
    anime: AnimeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(130.dp)
            .testTag("anime_poster_card_${anime.id}")
            .clickable { onClick() }
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster Image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(anime.image_poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = anime.title ?: "Anime poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0B0E14).copy(alpha = 0.85f))
                            )
                        )
                )

                // Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    anime.type?.let { type ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    anime.status?.let { status ->
                        val statusColor = if (status.equals("Ongoing", ignoreCase = true)) {
                            StatusOngoing
                        } else {
                            StatusCompleted
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor
                        ) {
                            Text(
                                text = if (status.equals("Ongoing", ignoreCase = true)) "ON" else "END",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Views or Day at bottom left inside card
                if (!anime.day.isNullOrEmpty()) {
                    Text(
                        text = anime.day.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = anime.title ?: "Tanpa Judul",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // Subtitle Info (Year / Rating / Type)
        val infoString = listOfNotNull(
            anime.year?.takeIf { it.isNotBlank() },
            (anime.type ?: anime.status)?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        if (infoString.isNotBlank()) {
            Text(
                text = infoString,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        // Views & Favorites
        val viewsText = formatCount(anime.views)
        val favoritesText = formatCount(anime.favorites)

        if (viewsText != null || favoritesText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
            ) {
                viewsText?.let {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Views",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (viewsText != null && favoritesText != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                favoritesText?.let {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorites",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Format angka besar ke format ringkas ala Indonesia: 1.874.111 -> "1,9jt",
 * 24.803 -> "24,8rb". Balikin null kalau input null/kosong/bukan angka.
 */
private fun formatCount(raw: String?): String? {
    val value = raw?.toLongOrNull() ?: return null
    return when {
        value >= 1_000_000 -> {
            val jt = value / 1_000_000.0
            "${"%.1f".format(jt).replace('.', ',')}jt"
        }
        value >= 1_000 -> {
            val rb = value / 1_000.0
            "${"%.1f".format(rb).replace('.', ',')}rb"
        }
        else -> value.toString()
    }
}
