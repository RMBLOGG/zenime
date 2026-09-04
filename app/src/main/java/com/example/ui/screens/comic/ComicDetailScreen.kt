package com.example.ui.screens.comic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.common.Result
import com.example.data.model.BacakomikChapterRef
import com.example.data.model.BacakomikDetail
import com.example.data.model.extractChapterLabel
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerBanner
import com.example.ui.theme.ZenimePrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailScreen(
    viewModel: ComicDetailViewModel,
    onBackClick: () -> Unit,
    onChapterClick: (chapterSlug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, modifier = modifier) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val s = state) {
                is Result.Loading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ShimmerBanner(modifier = Modifier.padding(top = 16.dp))
                    }
                }
                is Result.Error -> {
                    ErrorStateView(
                        message = s.message,
                        onRetry = { viewModel.loadDetail(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is Result.Success -> {
                    ComicDetailContent(detail = s.data, onChapterClick = onChapterClick)
                }
            }

            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComicDetailContent(
    detail: BacakomikDetail,
    onChapterClick: (String) -> Unit
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    val chapters = detail.chapters.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(320))
            ) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(detail.cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = detail.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = detail.title ?: "Tanpa Judul",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                detail.otherTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Info chips: status, type, rating
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.status?.takeIf { it.isNotBlank() }?.let { InfoChip(it) }
                    detail.type?.takeIf { it.isNotBlank() }?.let { InfoChip(it) }
                    detail.rating?.takeIf { it.isNotBlank() }?.let {
                        InfoChip(it, icon = Icons.Filled.Star)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Author / artist / release info
                val infoRows = listOfNotNull(
                    detail.author?.takeIf { it.isNotBlank() }?.let { "Author" to it },
                    detail.artist?.takeIf { it.isNotBlank() }?.let { "Artist" to it },
                    detail.release?.takeIf { it.isNotBlank() }?.let { "Rilis" to it },
                    detail.series?.takeIf { it.isNotBlank() }?.let { "Series" to it }
                )
                if (infoRows.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        infoRows.forEach { (label, value) ->
                            Row {
                                Text(
                                    text = "$label  ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Genres
                val genres = detail.genres.orEmpty()
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = genre.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Synopsis (expandable)
                detail.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    Text(
                        text = "Sinopsis",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable { synopsisExpanded = !synopsisExpanded }
                    )
                    Row(
                        modifier = Modifier
                            .clickable { synopsisExpanded = !synopsisExpanded }
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (synopsisExpanded) "Sembunyikan" else "Baca selengkapnya",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZenimePrimary
                        )
                        Icon(
                            imageVector = if (synopsisExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = ZenimePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Tombol baca dari chapter pertama (asumsi urutan terbaru di atas ->
                // chapter pertama = elemen paling akhir di list)
                if (chapters.isNotEmpty()) {
                    val firstChapter = chapters.last()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ZenimePrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(firstChapter.slug) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mulai Baca dari ${extractChapterLabel(firstChapter.slug)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text(
                    text = "Daftar Chapter (${chapters.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        itemsIndexed(chapters) { index, chapter ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220, delayMillis = (index % 12) * 25)) +
                    slideInVertically(tween(220, delayMillis = (index % 12) * 25)) { it / 4 }
            ) {
                ChapterRow(
                    chapter = chapter,
                    onClick = { onChapterClick(chapter.slug) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: BacakomikChapterRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = extractChapterLabel(chapter.slug),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            chapter.date?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
