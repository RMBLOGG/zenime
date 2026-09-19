package com.example.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AnimeItem
import com.example.data.model.CuplixItem
import com.example.data.model.GalleryImage
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay
import java.util.Locale

// ---------------------------------------------------------------------------
// Baris tab. Tiap tab: warna teks berubah halus + garis bawah yang "tumbuh"
// dari tengah saat dipilih.
// ---------------------------------------------------------------------------
@Composable
fun DetailTabRow(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            DetailTab.values().forEach { tab ->
                val isSelected = tab == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "tab-color"
                )
                val underline by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(260),
                    label = "tab-underline"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(tab) }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab.label,
                        color = textColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(underline)
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(ZenimePrimary)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CardOutlineBorder)
        )
    }
}

/** Isi tab muncul dengan fade + geser sedikit dari bawah setiap kali tab dibuka. */
@Composable
fun TabContentEnter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 12 }
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Season
// ---------------------------------------------------------------------------
@Composable
fun SeasonTabContent(
    state: PagedTabState<AnimeItem>,
    currentAnimeId: String,
    onAnimeClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    PagedTabHost(
        state = state,
        emptyMessage = "Anime ini belum punya season lain.",
        onRetry = onRetry
    ) { seasons ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            seasons.forEachIndexed { index, season ->
                AnimatedGridItem(index = index) {
                    SeasonCard(
                        season = season,
                        number = index + 1,
                        isCurrent = season.id == currentAnimeId,
                        onClick = { if (season.id != currentAnimeId) onAnimeClick(season.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(
    season: AnimeItem,
    number: Int,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 6f)
    val views = formatSeasonCount(season.views)
    val favorites = formatSeasonCount(season.favorites)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isCurrent) Modifier.border(1.5.dp, ZenimePrimary, shape) else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(season.image_cover?.takeIf { it.isNotBlank() } ?: season.image_poster)
                .crossfade(true)
                .build(),
            contentDescription = season.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradien gelap di bagian bawah supaya teks tetap terbaca.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        if (isCurrent) {
            Text(
                text = "Sedang dilihat",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ZenimePrimary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Season $number",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    shadow = textShadow
                ),
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (views != null) {
                    SeasonStat(
                        icon = Icons.Filled.PlayCircle,
                        text = "$views views",
                        color = SeasonViewsColor,
                        shadow = textShadow
                    )
                }
                if (favorites != null) {
                    SeasonStat(
                        icon = Icons.Filled.Star,
                        text = "$favorites favorites",
                        color = SeasonFavoritesColor,
                        shadow = textShadow
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonStat(
    icon: ImageVector,
    text: String,
    color: Color,
    shadow: Shadow
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(shadow = shadow),
            color = color,
            maxLines = 1
        )
    }
}

private val SeasonViewsColor = Color(0xFFFF3B3B)
private val SeasonFavoritesColor = Color(0xFFFFC928)

/** "431604" -> "431.604" (format Indonesia). Null kalau kosong / bukan angka. */
private fun formatSeasonCount(raw: String?): String? {
    val n = raw?.trim()?.toLongOrNull() ?: return raw?.takeIf { it.isNotBlank() }
    return java.text.NumberFormat.getInstance(Locale("id", "ID")).format(n)
}

// ---------------------------------------------------------------------------
// Cuplix per anime
// ---------------------------------------------------------------------------
@Composable
fun CuplixTabContent(
    state: PagedTabState<CuplixItem>,
    onClipClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    PagedTabHost(
        state = state,
        emptyMessage = "Belum ada Cuplix untuk anime ini.",
        onRetry = onRetry
    ) { clips ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            StaggeredGrid(items = clips, columns = 2, spacing = 12.dp) { clip ->
                CuplixCard(clip = clip, onClick = { onClipClick(clip.id) })
            }
            LoadMoreFooter(state = state, onLoadMore = onLoadMore)
        }
    }
}

@Composable
private fun CuplixCard(clip: CuplixItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(clip.urlThumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = clip.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = clip.caption?.takeIf { it.isNotBlank() } ?: clip.episode ?: "Cuplix",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${clip.username?.takeIf { it.isNotBlank() } ?: "Anonim"} • ${formatCount(clip.countLikes)} suka",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Cover & Poster (galeri kiriman pengguna)
// ---------------------------------------------------------------------------
@Composable
fun GalleryTabContent(
    state: PagedTabState<GalleryImage>,
    columns: Int,
    aspectRatio: Float,
    emptyMessage: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    var preview by remember { mutableStateOf<GalleryImage?>(null) }

    PagedTabHost(
        state = state,
        emptyMessage = emptyMessage,
        onRetry = onRetry
    ) { images ->
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            StaggeredGrid(items = images, columns = columns, spacing = 10.dp) { image ->
                GalleryCard(
                    image = image,
                    aspectRatio = aspectRatio,
                    onClick = { preview = image }
                )
            }
            LoadMoreFooter(state = state, onLoadMore = onLoadMore)
        }
    }

    preview?.let { image ->
        ImagePreviewDialog(image = image, onDismiss = { preview = null })
    }
}

@Composable
private fun GalleryCard(
    image: GalleryImage,
    aspectRatio: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        image.points?.takeIf { it.isNotBlank() && it != "0" }?.let { points ->
            Text(
                text = "$points poin",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(image: GalleryImage, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
            val caption = listOfNotNull(
                image.username?.let { "oleh $it" },
                image.points?.takeIf { it.isNotBlank() && it != "0" }?.let { "$it poin" }
            ).joinToString(" • ")
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bahan bersama
// ---------------------------------------------------------------------------

/**
 * Kerangka satu tab berdaftar: muat -> (galat | kosong | isi). Perpindahan
 * antar keadaan memakai crossfade supaya tidak "kedip".
 */
@Composable
private fun <T> PagedTabHost(
    state: PagedTabState<T>,
    emptyMessage: String,
    onRetry: () -> Unit,
    content: @Composable (items: List<T>) -> Unit
) {
    val phase = when {
        state.items.isNotEmpty() -> 3
        state.isLoading || (!state.loaded && state.error == null) -> 0
        state.error != null -> 1
        else -> 2
    }
    Crossfade(
        targetState = phase,
        animationSpec = tween(250),
        label = "tab-phase"
    ) { current ->
        when (current) {
            0 -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = ZenimePrimary
                )
            }
            1 -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.error ?: "Terjadi kesalahan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry) {
                    Text("Coba lagi", color = ZenimePrimary)
                }
            }
            2 -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> content(state.items)
        }
    }
}

/** Grid biasa (bukan Lazy) -- aman karena sudah berada di dalam satu item LazyColumn. */
@Composable
private fun <T> StaggeredGrid(
    items: List<T>,
    columns: Int,
    spacing: Dp,
    itemContent: @Composable (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowItems.forEachIndexed { colIndex, item ->
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedGridItem(index = rowIndex * columns + colIndex) {
                            itemContent(item)
                        }
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Item muncul berurutan (fade + membesar sedikit), jeda dibatasi supaya tidak lambat. */
@Composable
private fun AnimatedGridItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index % 9) * 40L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.92f)
    ) {
        content()
    }
}

@Composable
private fun LoadMoreFooter(
    state: PagedTabState<*>,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoadingMore -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = ZenimePrimary
            )
            state.error != null -> TextButton(onClick = onLoadMore) {
                Text("Gagal memuat. Coba lagi", color = ZenimePrimary)
            }
            state.hasMore -> TextButton(onClick = onLoadMore) {
                Text("Muat lebih banyak", color = ZenimePrimary)
            }
        }
    }
}

private fun formatCount(raw: String?): String {
    val n = raw?.toLongOrNull() ?: return "0"
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fjt", n / 1_000_000.0).replace(".0", "")
        n >= 1_000 -> String.format(Locale.US, "%.1frb", n / 1_000.0).replace(".0", "")
        else -> n.toString()
    }
}
