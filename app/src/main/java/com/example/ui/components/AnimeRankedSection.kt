package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AnimeItem

private val RankGold = Color(0xFFFFC107)
private val RankSilver = Color(0xFFE0E0E0)
private val RankBronze = Color(0xFFCD7F32)

/**
 * Section "tangga lagu": poster besar dengan nomor peringkat raksasa di pojok
 * kiri bawah (1-3 berwarna emas/perak/perunggu), jumlah views di kanan bawah,
 * lalu judul, genre, dan favorit di bawah poster. Urutan mengikuti urutan
 * dari server (endpoint popular sudah terurut).
 */
@Composable
fun AnimeRankedSection(
    title: String,
    items: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    maxItems: Int = 10,
    modifier: Modifier = Modifier
) {
    val ranked = items.take(maxItems)

    Column(modifier = modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = title, onSeeAllClick = onSeeAllClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(ranked, key = { _, anime -> anime.id }) { index, anime ->
                RankedCard(
                    rank = index + 1,
                    anime = anime,
                    onClick = { onAnimeClick(anime.id) }
                )
            }
        }
    }
}

@Composable
private fun RankedCard(
    rank: Int,
    anime: AnimeItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankSilver
        3 -> RankBronze
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(anime.image_poster)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradasi gelap di bawah supaya nomor & angka views tetap terbaca di poster terang.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 4f),
                        blurRadius = 10f
                    )
                ),
                color = rankColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 4.dp)
            )

            rankedCount(anime.views)?.let { views ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Views",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = views,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = anime.title ?: "Tanpa Judul",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val genre = anime.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            genre?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            rankedCount(anime.favorites)?.let { favorites ->
                if (genre != null) Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorites",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = favorites,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** 15.638.148 -> "15,6jt", 24.803 -> "24,8rb"; null kalau bukan angka. */
private fun rankedCount(raw: String?): String? {
    val value = raw?.toLongOrNull() ?: return null
    return when {
        value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000.0).replace('.', ',')}jt"
        value >= 1_000 -> "${"%.1f".format(value / 1_000.0).replace('.', ',')}rb"
        else -> value.toString()
    }
}
