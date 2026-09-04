package com.example.ui.screens.comic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ComicPosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.screens.search.GenrePillChip
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

@Composable
fun ComicScreen(
    viewModel: ComicViewModel,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestState by viewModel.latestState.collectAsStateWithLifecycle()
    val popularState by viewModel.popularState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val genresState by viewModel.genres.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Terbaru, 1 = Populer
    val isFiltering = query.isNotBlank() || selectedGenre != null

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20 }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ZenimeHeader(
                isScrolled = isScrolled,
                title = { ZenimeScreenTitle(title = "Komik") }
            )

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = {
                        Text("Cari judul komik...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Cari", tint = ZenimePrimary)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = ZenimePrimary,
                        unfocusedBorderColor = CardOutlineBorder
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("comic_search_input")
                )
            }

            // Genre Chips
            val genreList = (genresState as? com.example.data.common.Result.Success)?.data.orEmpty()
            if (genreList.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        GenrePillChip(
                            title = "Semua",
                            isSelected = selectedGenre == null,
                            onClick = { viewModel.selectGenre(null) }
                        )
                    }
                    items(genreList) { genre ->
                        val isSelected = selectedGenre?.slug == genre.slug
                        GenrePillChip(
                            title = genre.title,
                            isSelected = isSelected,
                            onClick = { viewModel.selectGenre(if (isSelected) null else genre) }
                        )
                    }
                }
            }

            // Tab Terbaru / Populer -- cuma ditampilin kalau lagi gak nyari/filter genre
            AnimatedVisibility(visible = !isFiltering) {
                ComicTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isFiltering) {
                    ComicResultGrid(
                        state = filterState,
                        gridState = gridState,
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
                    ) { tab ->
                        val state = if (tab == 0) latestState else popularState
                        ComicResultGrid(
                            state = state,
                            gridState = gridState,
                            emptyTitle = "Belum Ada Komik",
                            emptyDescription = "Konten belum tersedia saat ini.",
                            onComicClick = onComicClick,
                            onRetry = {
                                if (tab == 0) viewModel.loadLatest(forceRefresh = true)
                                else viewModel.loadPopular(forceRefresh = true)
                            },
                            onLoadMore = {
                                if (tab == 0) viewModel.loadMoreLatest() else viewModel.loadMorePopular()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Terbaru", "Populer")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) ZenimePrimary else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .weight(1f)
                    .then(if (!selected) Modifier.border(1.dp, CardOutlineBorder, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onTabSelected(index) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ComicResultGrid(
    state: ComicListState,
    gridState: LazyGridState,
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
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.fillMaxSize()
            ) {
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

                // Tombol "Load More" -- span 3 kolom penuh di baris terakhir.
                if (state.hasNextPage) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreButton(isLoading = state.isLoadingMore, onClick = onLoadMore)
                    }
                }
            }
        }
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
