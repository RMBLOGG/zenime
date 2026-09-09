package com.example.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.common.Result
import com.example.data.model.GenreItem
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerPosterItem
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeHeaderActionButton
import com.example.ui.components.ZenimeScreenTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()

    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
        }
    }
    val hasActiveFilter = selectedGenre != null || selectedStatus != null || selectedType != null || selectedSort != null

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
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
                title = { ZenimeScreenTitle(title = "Cari & Genre") },
                actions = {
                    ZenimeHeaderActionButton(
                        icon = Icons.Default.FilterList,
                        contentDescription = "Filter Options",
                        onClick = { showFilterSheet = true },
                        testTag = "filter_button",
                        badge = hasActiveFilter,
                        tint = if (hasActiveFilter) ZenimePrimary else Color.White
                    )
                }
            )
            // Search Bar Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            "Cari anime favoritmu...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ZenimePrimary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
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
                        .testTag("search_input_field")
                )
            }

            // Scrollable Genre Chips Row
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
                            onClick = { viewModel.applyFilter(null, selectedStatus, selectedType, selectedSort) }
                        )
                    }
                    items(genres) { genre ->
                        val slug = genre.getFilterValue()
                        val isSelected = selectedGenre == slug
                        GenrePillChip(
                            title = genre.getDisplayName(),
                            isSelected = isSelected,
                            onClick = {
                                val nextGenre = if (isSelected) null else slug
                                viewModel.applyFilter(nextGenre, selectedStatus, selectedType, selectedSort)
                            }
                        )
                    }
                }
            }

            // Results Grid
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = searchResults) {
                    is Result.Loading -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(9) {
                                ShimmerPosterItem(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is Result.Error -> {
                        ErrorStateView(
                            message = state.message,
                            onRetry = { viewModel.performSearch() }
                        )
                    }
                    is Result.Success -> {
                        val items = state.data
                        if (items.isEmpty()) {
                            EmptyStateView(
                                title = "Anime Tidak Ditemukan",
                                description = "Coba gunakan kata kunci lain atau pilih genre lain."
                            )
                        } else {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 110.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items, key = { it.id }) { anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(anime.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterBottomSheetContent(
                genres = genres,
                initialGenre = selectedGenre,
                initialStatus = selectedStatus,
                initialType = selectedType,
                initialSort = selectedSort,
                onApply = { genre, status, type, sort ->
                    viewModel.applyFilter(genre, status, type, sort)
                    showFilterSheet = false
                },
                onReset = {
                    viewModel.resetFilter()
                    showFilterSheet = false
                }
            )
        }
    }
}

@Composable
fun GenrePillChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) ZenimePrimary else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .then(
                if (!isSelected) Modifier.border(1.dp, CardOutlineBorder, CircleShape)
                else Modifier
            )
            .clickable { onClick() }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheetContent(
    genres: List<GenreItem>,
    initialGenre: String?,
    initialStatus: String?,
    initialType: String?,
    initialSort: String?,
    onApply: (genre: String?, status: String?, type: String?, sort: String?) -> Unit,
    onReset: () -> Unit
) {
    var tempGenre by remember { mutableStateOf(initialGenre) }
    var tempStatus by remember { mutableStateOf(initialStatus) }
    var tempType by remember { mutableStateOf(initialType) }
    var tempSort by remember { mutableStateOf(initialSort) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Filter Options",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Filter
        Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Semua", "ONGOING" to "Ongoing", "FINISHED" to "Completed").forEach { (valKey, label) ->
                FilterChip(
                    selected = tempStatus == valKey,
                    onClick = { tempStatus = valKey },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZenimePrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Type Filter
        Text("Tipe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Semua", "TV" to "TV Series", "Movie" to "Movie", "OVA" to "OVA", "ONA" to "ONA").forEach { (valKey, label) ->
                FilterChip(
                    selected = tempType == valKey,
                    onClick = { tempType = valKey },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZenimePrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sort Filter
        Text("Urutkan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Default", "latest" to "Terbaru", "popular" to "Terpopuler").forEach { (valKey, label) ->
                FilterChip(
                    selected = tempSort == valKey,
                    onClick = { tempSort = valKey },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZenimePrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }

            Button(
                onClick = { onApply(tempGenre, tempStatus, tempType, tempSort) },
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary),
                modifier = Modifier.weight(1f)
            ) {
                Text("Terapkan", color = Color.White)
            }
        }
    }
}
