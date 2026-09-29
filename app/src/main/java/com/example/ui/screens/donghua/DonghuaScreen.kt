package com.example.ui.screens.donghua

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import coil.request.ImageRequest
import com.example.data.api.AnichinNetwork
import com.example.data.model.AnichinCard
import com.example.data.model.AnichinHomeSection
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

private val ScreenBg = Color(0xFF0B0E14)

@Composable
fun DonghuaScreen(
    viewModel: DonghuaViewModel,
    onBackClick: () -> Unit,
    onDonghuaClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val ongoingState by viewModel.ongoingState.collectAsStateWithLifecycle()
    val allState by viewModel.allState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(DonghuaTab.LATEST) }
    var searchOpen by remember { mutableStateOf(false) }
    val isFiltering = query.isNotBlank() || selectedGenre != null

    LaunchedEffect(selectedTab) { viewModel.onTabSelected(selectedTab) }

    val searchAction: @Composable RowScope.() -> Unit = {
        IconButton(
            onClick = { searchOpen = true },
            modifier = Modifier.testTag("donghua_search_button")
        ) {
            Icon(Icons.Default.Search, contentDescription = "Cari", tint = Color.White)
        }
    }

    Scaffold(
        containerColor = ScreenBg,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ZenimeHeader(
                title = {
                    if (searchOpen) {
                        SearchField(
                            query = query,
                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                            onClose = {
                                viewModel.clearSearch()
                                searchOpen = false
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                            }
                            ZenimeScreenTitle(title = "Donghua")
                        }
                    }
                },
                actions = if (searchOpen) null else searchAction
            )

            if (genres.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        GenreChip(
                            title = "Semua",
                            selected = selectedGenre == null,
                            onClick = { viewModel.selectGenre(null) }
                        )
                    }
                    items(genres, key = { it.slug ?: it.name.orEmpty() }) { genre ->
                        val isSelected = selectedGenre?.slug == genre.slug
                        GenreChip(
                            title = genre.name ?: genre.slug.orEmpty(),
                            selected = isSelected,
                            onClick = { viewModel.selectGenre(if (isSelected) null else genre) }
                        )
                    }
                }
            }

            if (!isFiltering) {
                DonghuaTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            } else {
                HorizontalDivider(color = CardOutlineBorder.copy(alpha = 0.6f))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isFiltering -> DonghuaGrid(
                        state = filterState,
                        emptyTitle = "Donghua Tidak Ditemukan",
                        emptyDescription = "Coba kata kunci atau genre lain.",
                        onClick = onDonghuaClick,
                        onRetry = { viewModel.retryFilter() },
                        onLoadMore = { viewModel.loadMoreFilter() }
                    )
                    selectedTab == DonghuaTab.LATEST -> DonghuaHomeContent(
                        state = homeState,
                        onClick = onDonghuaClick,
                        onRetry = { viewModel.loadHome() }
                    )
                    selectedTab == DonghuaTab.ONGOING -> DonghuaGrid(
                        state = ongoingState,
                        emptyTitle = "Belum Ada Donghua",
                        emptyDescription = "Konten belum tersedia saat ini.",
                        onClick = onDonghuaClick,
                        onRetry = { viewModel.loadOngoing(1) },
                        onLoadMore = { viewModel.loadMoreOngoing() }
                    )
                    else -> DonghuaGrid(
                        state = allState,
                        emptyTitle = "Belum Ada Donghua",
                        emptyDescription = "Konten belum tersedia saat ini.",
                        onClick = onDonghuaClick,
                        onRetry = { viewModel.loadAll(1) },
                        onLoadMore = { viewModel.loadMoreAll() }
                    )
                }
            }
        }
    }
}

/* ───────────────────────── Header: pencarian ───────────────────────── */

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).size(20.dp)
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (query.isEmpty()) {
                Text(
                    "Cari donghua...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(ZenimePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("donghua_search_input")
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Tutup pencarian",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* ───────────────────────── Chip genre & tab ───────────────────────── */

@Composable
private fun GenreChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) ZenimePrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, ZenimePrimary) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** Tab teks dengan garis bawah aktif -- lebih ringan dari deretan tombol. */
@Composable
private fun DonghuaTabs(
    selectedTab: DonghuaTab,
    onTabSelected: (DonghuaTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DonghuaTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onTabSelected(tab) }
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (selected) ZenimePrimary else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = CardOutlineBorder.copy(alpha = 0.6f))
    }
}

/* ───────────────────────── Tab "Terbaru" ───────────────────────── */

@Composable
private fun DonghuaHomeContent(
    state: DonghuaHomeState,
    onClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when {
        state.isLoading -> ShimmerGrid()
        state.errorMessage != null -> ErrorStateView(message = state.errorMessage, onRetry = onRetry)
        state.isEmpty -> EmptyStateView(
            title = "Belum Ada Donghua",
            description = "Konten belum tersedia saat ini."
        )
        else -> {
            val sections = state.sections.filter { !it.cards.isNullOrEmpty() }
            // Hero: utamakan section rilisan terbaru, kalau gak ada pakai section pertama.
            val heroSection = sections.firstOrNull { it.section.orEmpty().contains("rilis", ignoreCase = true) }
                ?: sections.firstOrNull()
            val heroCards = heroSection?.cards.orEmpty()
                .filter { !it.slug.isNullOrBlank() && !it.thumbnail.isNullOrBlank() }
                .take(5)

            LazyColumn(
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (heroCards.isNotEmpty()) {
                    item(key = "hero") { HeroCarousel(cards = heroCards, onClick = onClick) }
                }
                items(sections, key = { it.section ?: it.hashCode().toString() }) { section ->
                    val ranked = section.section.orEmpty().contains("populer", ignoreCase = true)
                    Column {
                        SectionTitle(prettySectionName(section.section))
                        if (ranked) RankedRow(section, onClick) else PosterRow(section, onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
        color = Color.White,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    )
}

@Composable
private fun PosterRow(section: AnichinHomeSection, onClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            section.cards.orEmpty().filter { !it.slug.isNullOrBlank() },
            key = { it.slug!! }
        ) { card ->
            DonghuaCard(card = card, onClick = { card.slug?.let(onClick) }, modifier = Modifier.width(124.dp))
        }
    }
}

/** Baris peringkat: angka besar berkontur di kiri poster. */
@Composable
private fun RankedRow(section: AnichinHomeSection, onClick: (String) -> Unit) {
    val cards = section.cards.orEmpty().filter { !it.slug.isNullOrBlank() }.take(10)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(cards, key = { _, c -> c.slug!! }) { index, card ->
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clickable { card.slug?.let(onClick) }
            ) {
                Text(
                    text = "${index + 1}",
                    style = TextStyle(
                        fontSize = 84.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-4).sp,
                        color = Color.White.copy(alpha = 0.55f),
                        drawStyle = Stroke(width = 4f)
                    ),
                    modifier = Modifier.padding(end = 2.dp, bottom = 34.dp)
                )
                DonghuaCard(
                    card = card,
                    onClick = { card.slug?.let(onClick) },
                    modifier = Modifier.width(112.dp)
                )
            }
        }
    }
}

/* ───────────────────────── Hero carousel ───────────────────────── */

@Composable
private fun HeroCarousel(cards: List<AnichinCard>, onClick: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { cards.size })
    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            HeroCard(card = cards[page], onClick = { cards[page].slug?.let(onClick) })
        }
        if (cards.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                repeat(cards.size) { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(card: AnichinCard, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .testTag("donghua_hero_${card.slug}")
            .clickable { onClick() }
    ) {
        PosterImage(card = card, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to ScreenBg.copy(alpha = 0.96f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val meta = listOfNotNull(
                card.type?.takeIf { it.isNotBlank() && it != "Unknown" },
                card.eps?.let { "Episode $it" },
                card.status?.takeIf { it.isNotBlank() && it != "Unknown" }
            ).joinToString("  •  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = card.title ?: card.headline ?: "Tanpa Judul",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                modifier = Modifier.clickable { onClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Tonton",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/* ───────────────────────── Kartu poster ───────────────────────── */

@Composable
private fun PosterImage(card: AnichinCard, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(AnichinNetwork.imageUrl(card.thumbnail))
            .addHeader("User-Agent", AnichinNetwork.USER_AGENT)
            .addHeader("Referer", AnichinNetwork.sourceBase + "/")
            .crossfade(true)
            .build(),
        contentDescription = card.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun DonghuaCard(
    card: AnichinCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag("donghua_card_${card.slug}")
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            PosterImage(card = card, modifier = Modifier.fillMaxSize())

            // Badge tipe cuma buat yang bukan "Donghua" (mis. Movie) -- sisanya noise.
            card.type?.takeIf { it.isNotBlank() && it != "Unknown" && !it.equals("Donghua", ignoreCase = true) }
                ?.let { type ->
                    OverlayChip(
                        text = type,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    )
                }

            val badge = card.eps?.let { "Ep $it" }
                ?: card.status?.takeIf { it.isNotBlank() && it != "Unknown" }
            badge?.let {
                OverlayChip(
                    text = it,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = card.title ?: card.headline ?: "Tanpa Judul",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            ),
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OverlayChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.62f),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

// "rilisan_terbaru" -> "Rilisan Terbaru"
private fun prettySectionName(raw: String?): String =
    raw.orEmpty()
        .replace('_', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        .ifBlank { "Donghua" }

/* ───────────────────────── Grid (Ongoing / Semua / hasil filter) ───────────────────────── */

@Composable
private fun ShimmerGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(9) { ShimmerPosterItem(modifier = Modifier.fillMaxWidth()) }
    }
}

/** Berapa item dari ujung bawah grid sebelum halaman berikutnya mulai dimuat (2 baris). */
private const val PREFETCH_DISTANCE = 6

@Composable
private fun DonghuaGrid(
    state: DonghuaGridState,
    emptyTitle: String,
    emptyDescription: String,
    onClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    when {
        state.isInitialLoading -> ShimmerGrid()
        state.errorMessage != null && state.items.isEmpty() ->
            ErrorStateView(message = state.errorMessage, onRetry = onRetry)
        state.isEmpty -> EmptyStateView(title = emptyTitle, description = emptyDescription)
        else -> {
            val gridState = rememberLazyGridState()
            val currentState by rememberUpdatedState(state)
            val currentLoadMore by rememberUpdatedState(onLoadMore)

            // Ukuran list saat terakhir kali minta halaman berikutnya. Reset kalau list
            // diganti total (ganti tab/genre -> item pertama beda).
            var requestedAtSize by remember(state.items.firstOrNull()?.slug) { mutableIntStateOf(-1) }

            // Auto load more: begitu jempol scroll mendekati ujung bawah. Emit ulang tiap
            // total item berubah, jadi kalau halaman baru masih pendek dan ujung masih
            // kelihatan, halaman berikutnya langsung ikut dimuat.
            LaunchedEffect(gridState) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    Pair(lastVisible >= info.totalItemsCount - 1 - PREFETCH_DISTANCE, info.totalItemsCount)
                }
                    .distinctUntilChanged()
                    .collect { (nearEnd, _) ->
                        val s = currentState
                        // requestedAtSize mencegah loop retry otomatis kalau request gagal:
                        // gagal = ukuran list gak berubah -> nunggu user tap "Coba lagi".
                        if (nearEnd && s.hasNextPage && !s.isLoadingMore && s.items.size != requestedAtSize) {
                            requestedAtSize = s.items.size
                            currentLoadMore()
                        }
                    }
            }

            val loadFailed = state.hasNextPage && !state.isLoadingMore && requestedAtSize == state.items.size

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items, key = { it.slug!! }) { card: AnichinCard ->
                    DonghuaCard(card = card, onClick = { card.slug?.let(onClick) })
                }
                if (state.hasNextPage) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(
                            isLoading = state.isLoadingMore,
                            failed = loadFailed,
                            onRetry = {
                                requestedAtSize = state.items.size
                                onLoadMore()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Footer grid: spinner selama memuat; tombol "Coba lagi" cuma muncul kalau request gagal. */
@Composable
private fun LoadMoreFooter(isLoading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = ZenimePrimary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
            failed -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, CardOutlineBorder),
                modifier = Modifier.clickable { onRetry() }
            ) {
                Text(
                    "Gagal memuat. Coba lagi",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
