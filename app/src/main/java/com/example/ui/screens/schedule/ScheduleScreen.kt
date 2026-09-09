package com.example.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.AnimeItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.ShimmerHorizontalSection
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.StarYellow
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.launch

/**
 * A single row inside the flattened timeline list that backs the LazyColumn.
 */
private sealed class TimelineRow {
    data class Entry(val anime: AnimeItem, val day: Int, val isFirstOfDay: Boolean) : TimelineRow()
    data class Empty(val day: Int) : TimelineRow()
    data class Nav(val day: Int) : TimelineRow()
}

private val TimelineRow.dayIndex: Int
    get() = when (this) {
        is TimelineRow.Entry -> day
        is TimelineRow.Empty -> day
        is TimelineRow.Nav -> day
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()

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
                title = { ZenimeScreenTitle(title = "Jadwal Rilis Anime") }
            )

            when (val state = scheduleState) {
                is Result.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        ShimmerHorizontalSection()
                        ShimmerHorizontalSection()
                    }
                }
                is Result.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.loadFullWeek() }
                    )
                }
                is Result.Success -> {
                    if (state.data.all { it.anime.isEmpty() }) {
                        EmptyStateView(
                            title = "Tidak Ada Rilis",
                            description = "Belum ada anime yang terjadwal minggu ini."
                        )
                    } else {
                        ScheduleTimeline(
                            sections = state.data,
                            dayNames = viewModel.daysDisplayNames,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTimeline(
    sections: List<DaySection>,
    dayNames: List<String>,
    onAnimeClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Flatten every day's anime into one continuous list of rows, inserting a
    // day-navigation row between each day so the whole week scrolls as one thread.
    val rows = remember(sections) {
        buildList {
            sections.forEachIndexed { sectionIdx, section ->
                if (section.anime.isEmpty()) {
                    add(TimelineRow.Empty(section.dayIndex))
                } else {
                    section.anime.forEachIndexed { animeIdx, anime ->
                        add(TimelineRow.Entry(anime, section.dayIndex, isFirstOfDay = animeIdx == 0))
                    }
                }
                if (sectionIdx != sections.lastIndex) {
                    add(TimelineRow.Nav(section.dayIndex))
                }
            }
        }
    }

    // First row index for each day, so the nav buttons can jump straight to it.
    val dayStartRow = remember(rows) {
        val map = HashMap<Int, Int>()
        rows.forEachIndexed { idx, row ->
            when (row) {
                is TimelineRow.Entry -> if (row.isFirstOfDay) map.putIfAbsent(row.dayIndex, idx)
                is TimelineRow.Empty -> map.putIfAbsent(row.dayIndex, idx)
                is TimelineRow.Nav -> Unit
            }
        }
        map
    }

    // The day currently in view — this drives the header pill, purely from scroll position.
    val currentDayIndex by remember(rows) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            rows.getOrNull(idx)?.dayIndex ?: 0
        }
    }
    val currentCount = sections.getOrNull(currentDayIndex)?.anime?.size ?: 0

    // Day indicator + anime count — updates automatically as the list scrolls, no taps needed.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = ZenimePrimary) {
            Text(
                text = dayNames[currentDayIndex],
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.border(1.dp, CardOutlineBorder, CircleShape)
        ) {
            Text(
                text = "$currentCount Anime",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            rows,
            key = { _, row ->
                when (row) {
                    is TimelineRow.Entry -> "entry_${row.anime.id}_${row.dayIndex}"
                    is TimelineRow.Empty -> "empty_${row.dayIndex}"
                    is TimelineRow.Nav -> "nav_${row.dayIndex}"
                }
            }
        ) { _, row ->
            when (row) {
                is TimelineRow.Entry -> {
                    TimelineEntryRow(
                        time = formatKeyTime(row.anime.key_time),
                        anime = row.anime,
                        onClick = { onAnimeClick(row.anime.id) }
                    )
                }
                is TimelineRow.Empty -> {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(CardOutlineBorder)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Tidak ada rilis hari ${dayNames[row.dayIndex]}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                is TimelineRow.Nav -> {
                    val nextIdx = row.dayIndex + 1
                    val prevIdx = row.dayIndex - 1
                    DayNavRow(
                        prevDayName = dayNames.getOrNull(prevIdx),
                        nextDayName = dayNames.getOrNull(nextIdx),
                        onPrev = {
                            dayStartRow[prevIdx]?.let { target ->
                                coroutineScope.launch { listState.animateScrollToItem(target) }
                            }
                        },
                        onNext = {
                            dayStartRow[nextIdx]?.let { target ->
                                coroutineScope.launch { listState.animateScrollToItem(target) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryRow(
    time: String,
    anime: AnimeItem,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Timeline gutter: connecting line + time label + dot
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(CardOutlineBorder)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = if (time == "--:--") MaterialTheme.colorScheme.onSurfaceVariant else StarYellow
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(ZenimePrimary)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        ScheduleHorizontalCard(
            anime = anime,
            onClick = onClick,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun DayNavRow(
    prevDayName: String?,
    nextDayName: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, top = 4.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prevDayName != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .border(1.dp, CardOutlineBorder, CircleShape)
                    .clickable { onPrev() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(prevDayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        if (nextDayName != null) {
            Surface(
                shape = CircleShape,
                color = ZenimePrimary,
                modifier = Modifier.clickable { onNext() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(nextDayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
    }
}

/** Pulls "HH:mm" out of a "yyyy-MM-dd HH:mm:ss" key_time string, falling back to "--:--". */
private fun formatKeyTime(keyTime: String?): String {
    if (keyTime.isNullOrBlank()) return "--:--"
    val timePart = keyTime.substringAfter(' ', missingDelimiterValue = "")
    return if (timePart.length >= 5) timePart.substring(0, 5) else "--:--"
}

@Composable
fun ScheduleHorizontalCard(
    anime: AnimeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardOutlineBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(anime.image_poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title ?: "Tanpa Judul",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    anime.type?.let { type ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ZenimePrimary
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    anime.status?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                anime.time?.let { relative ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = relative,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
