package com.example.ui.screens.donghua

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.AnichinNetwork
import com.example.data.common.Result
import com.example.data.model.AnichinAnimeDetail
import com.example.data.model.AnichinEpisodeRef
import com.example.ui.components.ErrorStateView
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary
import com.example.util.friendlyErrorMessage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonghuaDetailScreen(
    viewModel: DonghuaDetailViewModel,
    onBackClick: () -> Unit,
    onEpisodeClick: (episodeSlug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val s = state) {
                is Result.Loading -> androidx.compose.material3.CircularProgressIndicator(
                    color = ZenimePrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                is Result.Error -> ErrorStateView(
                    message = friendlyErrorMessage(s.exception, "Gagal memuat detail donghua."),
                    onRetry = { viewModel.load() }
                )
                is Result.Success -> DetailContent(
                    detail = s.data,
                    onEpisodeClick = onEpisodeClick
                )
            }

            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: AnichinAnimeDetail,
    onEpisodeClick: (String) -> Unit
) {
    var expandedSinopsis by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(AnichinNetwork.imageUrl(detail.thumbnail))
                            .addHeader("User-Agent", AnichinNetwork.USER_AGENT)
                            .addHeader("Referer", AnichinNetwork.sourceBase + "/")
                            .crossfade(true)
                            .build(),
                        contentDescription = detail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    detail.rating?.let { rating ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(rating, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    // Info dari situs sumber: tampilkan beberapa yang umum aja (key-nya dinamis)
                    orderedInfo(detail.info).take(5).forEach { (k, v) ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${prettyKey(k)}: $v",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (detail.genres.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    detail.genres.forEach { g ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = g,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (detail.sinopsis.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { expandedSinopsis = !expandedSinopsis }
                ) {
                    Text(
                        text = "Sinopsis",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail.sinopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expandedSinopsis) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = if (expandedSinopsis) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = ZenimePrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        item {
            Text(
                text = "Episode (${detail.episodes.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        if (detail.episodes.isEmpty()) {
            item {
                Text(
                    text = "Belum ada episode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(detail.episodes, key = { it.slug }) { ep ->
                EpisodeRow(ep = ep, onClick = { onEpisodeClick(ep.slug) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: AnichinEpisodeRef, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("donghua_episode_${ep.slug}")
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ZenimePrimary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ep.subtitle ?: ep.name ?: "Episode ${ep.episode.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                ep.date?.takeIf { it.isNotBlank() && it != "Unknown Date" }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// "diperbarui_pada" -> "Diperbarui pada"
private fun prettyKey(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

// Field info dari situs sumber (key-nya dinamis). Tampilkan yang berguna dulu,
// buang yang isinya metadata admin (tanggal posting/update, nama pengunggah).
private val PREFERRED_INFO = listOf(
    "status", "tipe", "type", "studio", "durasi", "negara", "network", "season", "tanggal_rilis", "rilis"
)
private val HIDDEN_INFO = setOf("diperbarui_pada", "diposting_oleh", "ditambahkan", "updated_on", "posted_by", "released_on")

private fun orderedInfo(info: Map<String, String>): List<Pair<String, String>> {
    val visible = info.filterKeys { it !in HIDDEN_INFO }
    val first = PREFERRED_INFO.mapNotNull { k -> visible[k]?.let { k to it } }
    val rest = visible.filterKeys { it !in PREFERRED_INFO }.toList()
    return first + rest
}
