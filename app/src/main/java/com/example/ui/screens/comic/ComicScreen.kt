package com.example.ui.screens.comic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.comic.ComicTab
import com.example.ui.components.ComicHeroCarousel
import com.example.ui.components.ComicPosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeHeaderActionButton
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

@Composable
fun ComicScreen(
    viewModel: ComicViewModel,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val sourceId by viewModel.selectedSource.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val genresState by viewModel.genres.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()

    val isFiltering = query.isNotBlank() || selectedGenre != null
    var searchOpen by remember { mutableStateOf(false) }
    val showSearch = searchOpen || query.isNotBlank()

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20 }
    }

    // Ganti sumber / tab -> balik ke paling atas daftar.
    LaunchedEffect(sourceId, selectedTab) { gridState.scrollToItem(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header ringkas: judul + pemilih sumber + tombol cari dalam satu baris.
            // Tombol cari membuka kolom cari yang menggantikan header.
            if (showSearch) {
                ComicSearchBar(
                    query = query,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onClose = {
                        viewModel.clearSearch()
                        searchOpen = false
                    }
                )
            } else {
                ZenimeHeader(
                    isScrolled = isScrolled,
                    title = { ZenimeScreenTitle(title = "Komik") },
                    actions = {
                        ComicSourceSwitcher(
                            labels = viewModel.sources.map { it.label },
                            selectedIndex = viewModel.sources.indexOf(sourceId),
                            onTabSelected = { viewModel.selectSource(viewModel.sources[it]) }
                        )
                        ZenimeHeaderActionButton(
                            icon = Icons.Default.Search,
                            contentDescription = "Cari",
                            onClick = { searchOpen = true },
                            testTag = "comic_search_button"
                        )
                    }
                )
            }

            // Genre Chips
            val genreList = (genresState as? com.example.data.common.Result.Success)?.data.orEmpty()
            if (genreList.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        ComicGenreChip(
                            title = "Semua",
                            isSelected = selectedGenre == null,
                            onClick = { viewModel.selectGenre(null) }
                        )
                    }
                    items(genreList) { genre ->
                        val isSelected = selectedGenre?.slug == genre.slug
                        ComicGenreChip(
                            title = genre.title,
                            isSelected = isSelected,
                            onClick = { viewModel.selectGenre(if (isSelected) null else genre) }
                        )
                    }
                }
            }

            // Tab daftar (beda tiap sumber) -- cuma ditampilin kalau lagi gak nyari/filter genre
            AnimatedVisibility(visible = !isFiltering) {
                ComicSectionTabs(
                    tabs = tabs,
                    selectedId = selectedTab,
                    onSelect = { viewModel.selectTab(it) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isFiltering) {
                    ComicResultGrid(
                        state = filterState,
                        gridState = gridState,
                        sectionTitle = if (query.isNotBlank()) "Hasil untuk \"$query\"" else selectedGenre?.title,
                        showHero = false,
                        emptyTitle = "Komik Tidak Ditemukan",
                        emptyDescription = "Coba kata kunci atau genre lain.",
                        onComicClick = onComicClick,
                        onRetry = {
                            if (query.isNotBlank()) viewModel.onSearchQueryChange(query)
                            else viewModel.selectGenre(selectedGenre)
                        },
                        onLoadMore = { viewModel.loadMoreFilter() }
                    )
                } else {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "comicTabContent"
                    ) { _ ->
                        ComicResultGrid(
                            state = listState,
                            gridState = gridState,
                            sectionTitle = tabs.firstOrNull { it.id == selectedTab }?.label,
                            showHero = selectedTab == tabs.firstOrNull()?.id,
                            emptyTitle = "Belum Ada Komik",
                            emptyDescription = "Konten belum tersedia saat ini.",
                            onComicClick = onComicClick,
                            onRetry = { viewModel.loadTab(forceRefresh = true) },
                            onLoadMore = { viewModel.loadMoreTab() }
                        )
                    }
                }
            }
        }
    }
}

/** Pemilih sumber ala segmented control versi ringkas (muat di header). */
@Composable
private fun ComicSourceSwitcher(
    labels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, CardOutlineBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            val bg by animateColorAsState(
                targetValue = if (selected) ZenimePrimary else Color.Transparent,
                animationSpec = tween(200),
                label = "sourceBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "sourceFg"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Kolom cari yang menggantikan header saat tombol cari ditekan. */
@Composable
private fun ComicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, ZenimePrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = ZenimePrimary,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(20.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(ZenimePrimary),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .focusRequester(focusRequester)
                .testTag("comic_search_input"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Cari judul komik...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            }
        )
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Tutup pencarian",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Tab daftar (Terbaru / Populer / ...) gaya teks + garis bawah aktif. */
@Composable
private fun ComicSectionTabs(
    tabs: List<ComicTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs, key = { it.id }) { tab ->
            val selected = tab.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab.id) }
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                    ),
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (selected) ZenimePrimary else Color.Transparent)
                )
            }
        }
    }
}

/** Chip genre: outline tipis, aktif = tint merah. */
@Composable
private fun ComicGenreChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ZenimePrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) ZenimePrimary.copy(alpha = 0.7f) else CardOutlineBorder
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ComicSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(ZenimePrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun ComicResultGrid(
    state: ComicListState,
    gridState: LazyGridState,
    sectionTitle: String?,
    showHero: Boolean,
    emptyTitle: String,
    emptyDescription: String,
    onComicClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isInitialLoading -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(9) { ShimmerPosterItem(modifier = Modifier.fillMaxWidth()) }
            }
        }
        state.errorMessage != null && state.items.isEmpty() -> {
            ErrorStateView(message = state.errorMessage, onRetry = onRetry)
        }
        state.isEmpty -> {
            EmptyStateView(title = emptyTitle, description = emptyDescription)
        }
        else -> {
            // Auto load more: muat halaman berikutnya begitu scroll mendekati
            // ujung daftar. requestedFor nyatet halaman yang sudah diminta, jadi
            // kalau gagal gak diulang terus-terusan -- user dapat tombol coba lagi.
            val requestedFor = remember(sectionTitle, state.items.firstOrNull()?.slug) {
                mutableIntStateOf(-1)
            }
            val nearEnd by remember(state.hasNextPage, state.isLoadingMore) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    state.hasNextPage && !state.isLoadingMore && last >= info.totalItemsCount - 8
                }
            }
            LaunchedEffect(nearEnd, state.currentPage, state.items.size) {
                if (nearEnd && requestedFor.intValue != state.currentPage) {
                    requestedFor.intValue = state.currentPage
                    onLoadMore()
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.fillMaxSize()
            ) {
                // Carousel unggulan -- cuma di tab pertama & kalau datanya cukup.
                if (showHero && state.items.size >= 5) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "__hero") {
                        ComicHeroCarousel(
                            items = state.items.take(5),
                            onComicClick = onComicClick
                        )
                    }
                }

                if (!sectionTitle.isNullOrBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "__section") {
                        ComicSectionTitle(title = sectionTitle)
                    }
                }

                items(state.items, key = { it.slug }) { comic ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 8 }
                    ) {
                        ComicPosterCard(
                            comic = comic,
                            onClick = { onComicClick(comic.slug) }
                        )
                    }
                }

                // Footer: spinner saat memuat, tombol coba lagi kalau auto-load gagal.
                if (state.hasNextPage) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "__footer") {
                        LoadMoreFooter(
                            isLoading = state.isLoadingMore,
                            showRetry = !state.isLoadingMore && requestedFor.intValue == state.currentPage,
                            onRetry = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    isLoading: Boolean,
    showRetry: Boolean,
    onRetry: () -> Unit
) {
    when {
        isLoading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = ZenimePrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        }
        showRetry -> LoadMoreButton(isLoading = false, onClick = onRetry)
        else -> Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun LoadMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .border(1.dp, CardOutlineBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = !isLoading) { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Memuat...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Muat Lebih Banyak",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = ZenimePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
