package com.example.ui.screens.donghua

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.screens.detail.TabContentEnter
import com.example.ui.screens.detail.TabSlide
import com.example.ui.screens.detail.tabSlide
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.StarYellow
import com.example.ui.theme.ZenimePrimary
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.launch

private const val EPISODE_COLUMNS = 3

private enum class DonghuaDetailTab(val label: String) {
    EPISODE("Episode"),
    INFO("Info")
}

/**
 * Halaman detail donghua -- desainnya disamain sama halaman detail anime:
 * hero full-bleed + judul di atas gradient + tombol play bulat, baris tab
 * (Episode | Info), dan daftar episode bentuk grid 3 kolom.
 */
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
                is Result.Loading -> {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    RoundBackButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                is Result.Error -> {
                    ErrorStateView(
                        message = friendlyErrorMessage(s.exception, "Gagal memuat detail donghua."),
                        onRetry = { viewModel.load() }
                    )
                    RoundBackButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                // Pas sukses, tombol back ada di dalam hero (sama kayak detail anime).
                is Result.Success -> DetailContent(
                    detail = s.data,
                    onBackClick = onBackClick,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }
    }
}

@Composable
private fun RoundBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Kembali",
            tint = Color.White
        )
    }
}

@Composable
private fun DetailContent(
    detail: AnichinAnimeDetail,
    onBackClick: () -> Unit,
    onEpisodeClick: (String) -> Unit
) {
    // Tab aktif disimpan sebagai nama enum supaya selamat dari rotasi layar.
    var selectedTabName by rememberSaveable { mutableStateOf(DonghuaDetailTab.EPISODE.name) }
    val selectedTab = DonghuaDetailTab.values().firstOrNull { it.name == selectedTabName } ?: DonghuaDetailTab.EPISODE

    // Transisi pindah tab (klik ATAU geser) -- pola sama persis dengan detail anime.
    val tabScope = rememberCoroutineScope()
    val tabTransition = remember { Animatable(1f) }
    var tabDirection by remember { mutableIntStateOf(1) }
    val tabSlide = remember(tabDirection) { TabSlide(tabTransition, tabDirection) }
    val selectTab: (DonghuaDetailTab) -> Unit = { tab ->
        if (tab != selectedTab) {
            tabScope.launch {
                tabDirection = if (tab.ordinal > selectedTab.ordinal) 1 else -1
                tabTransition.snapTo(0f)
                selectedTabName = tab.name
                tabTransition.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
        }
    }

    val listState = rememberLazyListState()
    // Pindah tab saat list udah di-scroll jauh: balik ke baris tab dulu.
    LaunchedEffect(selectedTab) {
        if (listState.firstVisibleItemIndex > 1) {
            listState.scrollToItem(1)
        }
    }

    val episodeRows = remember(detail.episodes) { detail.episodes.chunked(EPISODE_COLUMNS) }
    val startEpisode = remember(detail.episodes) { startEpisodeOf(detail.episodes) }
    val meta = listOfNotNull(
        (detail.info["tipe"] ?: detail.info["type"])?.trim()?.takeIf { it.isNotEmpty() },
        detail.info["status"]?.trim()?.takeIf { it.isNotEmpty() },
        detail.info["negara"]?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" • ")
    val rating = detail.rating?.trim()?.takeIf { it.isNotEmpty() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 36.dp),
        modifier = Modifier
            .fillMaxSize()
            // Geser kiri/kanan = pindah tab. Geseran vertikal tetap milik LazyColumn.
            .pointerInput(selectedTab) {
                val threshold = 64.dp.toPx()
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val tabs = DonghuaDetailTab.values()
                        val index = selectedTab.ordinal
                        if (total <= -threshold && index < tabs.lastIndex) {
                            selectTab(tabs[index + 1])
                        }
                        if (total >= threshold && index > 0) {
                            selectTab(tabs[index - 1])
                        }
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        total += dragAmount
                        change.consume()
                    }
                )
            }
    ) {
        // 1. Hero full-bleed: poster + gradient + judul + tombol play
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                // Donghua cuma punya poster potret (bukan cover landscape), jadi
                // di-crop dari atas biar wajah/karakter tetap kelihatan.
                AsyncImage(
                    model = rememberAnichinImageRequest(detail.thumbnail),
                    contentDescription = detail.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient: gelap tipis di atas, menyatu ke warna background di bawah
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    Color(0xFF0B0E14).copy(alpha = 0.7f),
                                    Color(0xFF0B0E14)
                                )
                            )
                        )
                )

                RoundBackButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // Judul + rating + info singkat
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 96.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            lineHeight = 32.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (rating != null || meta.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (rating != null) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Rating",
                                    tint = StarYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = rating,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            if (meta.isNotBlank()) {
                                Text(
                                    text = if (rating != null) "• $meta" else meta,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Tombol play bulat: mulai dari episode paling awal
                if (startEpisode != null) {
                    Surface(
                        shape = CircleShape,
                        color = ZenimePrimary,
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 12.dp)
                            .size(64.dp)
                            .clickable { onEpisodeClick(startEpisode.slug) }
                            .testTag("play_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Mainkan episode pertama",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Baris tab
        item(key = "detail_tabs") {
            DonghuaDetailTabRow(selected = selectedTab, onSelect = selectTab)
        }

        item(key = "tab_top_space") {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (selectedTab == DonghuaDetailTab.EPISODE) {
            // 3. Grid episode 3 kolom (satu baris = satu item LazyColumn, jadi ringan)
            if (episodeRows.isEmpty()) {
                item(key = "episode_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Episode belum tersedia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(episodeRows, key = { row -> "ep_row_${row.first().slug}" }) { row ->
                    Row(
                        modifier = Modifier
                            .tabSlide(tabSlide)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { ep ->
                            Box(modifier = Modifier.weight(1f)) {
                                DonghuaEpisodeCard(
                                    ep = ep,
                                    fallbackThumbnail = detail.thumbnail,
                                    onClick = { onEpisodeClick(ep.slug) }
                                )
                            }
                        }
                        repeat(EPISODE_COLUMNS - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            item(key = "tab_content_INFO") {
                TabContentEnter(slide = tabSlide) {
                    InfoTabContent(detail = detail)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Baris tab (gaya sama dengan DetailTabRow anime: teks berubah warna + garis
// bawah yang tumbuh dari tengah).
// ---------------------------------------------------------------------------
@Composable
private fun DonghuaDetailTabRow(
    selected: DonghuaDetailTab,
    onSelect: (DonghuaDetailTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            DonghuaDetailTab.values().forEach { tab ->
                val isSelected = tab == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "donghua-tab-color"
                )
                val underline by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(260),
                    label = "donghua-tab-underline"
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
                            fontSize = 12.sp
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

// ---------------------------------------------------------------------------
// Kartu episode: thumbnail persegi + nomor di pojok kanan bawah.
// ---------------------------------------------------------------------------
@Composable
private fun DonghuaEpisodeCard(
    ep: AnichinEpisodeRef,
    fallbackThumbnail: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val ownThumbnail = ep.thumbnail?.takeIf { it.isNotBlank() }
    val thumbnail = ownThumbnail ?: fallbackThumbnail

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("donghua_episode_${ep.slug}")
            .clickable { onClick() }
    ) {
        if (!thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = rememberAnichinImageRequest(thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Thumbnail episode biasanya landscape (tengah); poster fallback potret (atas)
                alignment = if (ownThumbnail != null) Alignment.Center else Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        // Nomor episode: "tab" di pojok kanan bawah, warnanya sama dengan latar halaman
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 18.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Text(
                text = episodeLabelOrNull(ep) ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tab Info: genre, sinopsis, rincian (semua dari respons detail, tanpa API tambahan).
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoTabContent(detail: AnichinAnimeDetail) {
    var expanded by remember { mutableStateOf(false) }

    val synopsis = remember(detail) {
        val cleaned = detail.sinopsis
            .replace("\r\n", "\n").replace("\r", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        // Data sumber kadang nempelin judul di awal sinopsis ("JudulYun Qingyan, ...")
        if (cleaned.startsWith(detail.name)) cleaned.removePrefix(detail.name).trim() else cleaned
    }
    val rows = remember(detail) { orderedInfo(detail.info).map { (k, v) -> prettyKey(k) to v } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (detail.genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.genres.forEach { genre ->
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, CardOutlineBorder, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (synopsis.isNotBlank()) {
            InfoSection(title = "Sinopsis") {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(animationSpec = tween(250))
                )
                if (synopsis.length > 220) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            text = if (expanded) "Tutup" else "Baca selengkapnya",
                            color = ZenimePrimary
                        )
                    }
                }
            }
        }

        if (rows.isNotEmpty()) {
            InfoSection(title = "Rincian") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, CardOutlineBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    rows.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.38f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(0.62f)
                            )
                        }
                        if (index < rows.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(CardOutlineBorder.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }

        if (detail.genres.isEmpty() && synopsis.isBlank() && rows.isEmpty()) {
            Text(
                text = "Belum ada informasi untuk donghua ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

/** Gambar dari situs sumber butuh User-Agent + Referer, kalau nggak sering diblok. */
@Composable
private fun rememberAnichinImageRequest(path: String?): ImageRequest {
    val context = LocalContext.current
    return remember(context, path) {
        ImageRequest.Builder(context)
            .data(AnichinNetwork.imageUrl(path))
            .addHeader("User-Agent", AnichinNetwork.USER_AGENT)
            .addHeader("Referer", AnichinNetwork.sourceBase + "/")
            .crossfade(true)
            .build()
    }
}

private val NUMBER_REGEX = Regex("\\d+(?:[.,]\\d+)?")
// cocok "episode" maupun typo "epsiode" yang ada di beberapa slug/judul situs sumber
private val EPISODE_IN_TEXT_REGEX = Regex("(?i)ep[a-z]*sode\\s*(\\d+(?:[.,]\\d+)?)")

/** Nomor episode: dari field `episode` kalau ada, kalau nggak diambil dari judul. */
private fun episodeLabelOrNull(ep: AnichinEpisodeRef): String? =
    ep.episode?.let { NUMBER_REGEX.find(it)?.value }
        ?: EPISODE_IN_TEXT_REGEX.find(ep.subtitle ?: ep.name ?: "")?.groupValues?.get(1)

/**
 * Episode buat tombol play. API ngasih urutan terbaru dulu, jadi ambil yang nomornya
 * paling kecil; kalau ada nomor yang gak kebaca, ambil item terakhir di daftar.
 */
private fun startEpisodeOf(episodes: List<AnichinEpisodeRef>): AnichinEpisodeRef? {
    if (episodes.isEmpty()) return null
    val numbered = episodes.mapNotNull { ep ->
        episodeLabelOrNull(ep)?.replace(',', '.')?.toDoubleOrNull()?.let { ep to it }
    }
    return if (numbered.size == episodes.size) numbered.minBy { it.second }.first else episodes.last()
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
