package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AnimeItem

/**
 * Section bergaya "banner" (dipakai "Sedang Hangat" dan "Jas Por Yu"):
 * kartu cover lebar 16:9 yang bergulir ke samping, dan di bawah tiap cover ada
 * baris ringkas -- poster mini, genre, judul, lalu jumlah views & favorit.
 * Lebar kartu ~62% layar, jadi kartu berikutnya selalu mengintip dan
 * jelas bisa digeser.
 */
@Composable
fun AnimeCoverBannerSection(
    title: String,
    items: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardWidth = (LocalConfiguration.current.screenWidthDp * 0.62f).dp

    Column(modifier = modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = title, onSeeAllClick = onSeeAllClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                CoverBannerCard(
                    anime = anime,
                    width = cardWidth,
                    onClick = { onAnimeClick(anime.id) }
                )
            }
        }
    }
}

@Composable
private fun CoverBannerCard(
    anime: AnimeItem,
    width: Dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Cover lebar kalau ada; kalau tidak, pakai poster (dipotong tengah).
    val coverUrl = anime.image_cover?.takeIf { it.isNotBlank() } ?: anime.image_poster

    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(anime.image_poster)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(46.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                anime.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = anime.title ?: "Tanpa Judul",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val viewsText = compactCount(anime.views)
                val favoritesText = compactCount(anime.favorites)
                if (viewsText != null || favoritesText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        viewsText?.let {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Views",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (viewsText != null && favoritesText != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        favoritesText?.let {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Favorites",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** 1.874.111 -> "1,9jt", 24.803 -> "24,8rb"; null kalau bukan angka. */
private fun compactCount(raw: String?): String? {
    val value = raw?.toLongOrNull() ?: return null
    return when {
        value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000.0).replace('.', ',')}jt"
        value >= 1_000 -> "${"%.1f".format(value / 1_000.0).replace('.', ',')}rb"
        else -> value.toString()
    }
}
