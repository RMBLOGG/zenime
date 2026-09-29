package com.example.ui.screens.donghua

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AnichinCard
import com.example.ui.components.DonghuaPosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.screens.search.GenrePillChip
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

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
    val isFiltering = query.isNotBlank() || selectedGenre != null

    LaunchedEffect(selectedTab) { viewModel.onTabSelected(selectedTab) }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        ZenimeScreenTitle(title = "Donghua")
                    }
                }
            )

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Cari donghua...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", tint = ZenimePrimary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
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
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("donghua_search_input")
            )

            if (genres.isNotEmpty()) {
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
                    items(genres, key = { it.slug ?: it.name.orEmpty() }) { genre ->
                        val isSelected = selectedGenre?.slug == genre.slug
                        GenrePillChip(
                            title = genre.name ?: genre.slug.orEmpty(),
                            isSelected = isSelected,
                            onClick = { viewModel.selectGenre(if (isSelected) null else genre) }
                        )
                    }
                }
            }

            if (!isFiltering) {
                DonghuaTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
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

@Composable
private fun DonghuaTabRow(
    selectedTab: DonghuaTab,
    onTabSelected: (DonghuaTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DonghuaTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) ZenimePrimary else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .weight(1f)
                    .then(if (!selected) Modifier.border(1.dp, CardOutlineBorder, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onTabSelected(tab) }
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** Tab "Terbaru": section-section dari GET / (tiap section = baris horizontal). */
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
        else -> LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.sections, key = { it.section ?: it.hashCode().toString() }) { section ->
                Column {
                    Text(
                        text = prettySectionName(section.section),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            section.cards.orEmpty().filter { !it.slug.isNullOrBlank() },
                            key = { it.slug!! }
                        ) { card ->
                            DonghuaPosterCard(
                                card = card,
                                onClick = { card.slug?.let(onClick) },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }
        }
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
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.items, key = { it.slug!! }) { card: AnichinCard ->
                DonghuaPosterCard(
                    card = card,
                    onClick = { card.slug?.let(onClick) }
                )
            }
            if (state.hasNextPage) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreButton(isLoading = state.isLoadingMore, onClick = onLoadMore)
                }
            }
        }
    }
}

@Composable
private fun LoadMoreButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
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
                    CircularProgressIndicator(color = ZenimePrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Memuat...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "Muat Lebih Banyak",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = ZenimePrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
