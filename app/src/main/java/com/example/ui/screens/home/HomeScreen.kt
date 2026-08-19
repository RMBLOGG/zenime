package com.example.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.common.Result
import com.example.data.model.AnimeItem
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.ErrorStateView
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerBanner
import com.example.ui.components.ShimmerHorizontalSection
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeHeaderActionButton
import com.example.ui.components.ZenimeLogoTitle
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSeeAllOngoingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val heroStyle by viewModel.heroStyle.collectAsStateWithLifecycle()
    val heroAutoplay by viewModel.heroAutoplay.collectAsStateWithLifecycle()
    val heroIntervalMs by viewModel.heroIntervalMs.collectAsStateWithLifecycle()
    val heroItemCount by viewModel.heroItemCount.collectAsStateWithLifecycle()
    val heroSource by viewModel.heroSource.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 30
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = homeState) {
                is Result.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 54.dp, bottom = 8.dp)
                    ) {
                        ShimmerBanner()
                        Spacer(modifier = Modifier.height(16.dp))
                        ShimmerHorizontalSection()
                        ShimmerHorizontalSection()
                    }
                }
                is Result.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 54.dp)
                    ) {
                        ErrorStateView(
                            message = state.message,
                            onRetry = { viewModel.loadHome(forceConfigRefresh = true) }
                        )
                    }
                }
                is Result.Success -> {
                    val data = state.data
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { viewModel.loadHome() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 110.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Hero Banner Carousel -- sumber & jumlah item
                            // ngikutin preferensi "Sumber Banner" & "Jumlah
                            // Anime di Carousel" dari Pengaturan, fallback ke
                            // urutan lama (hot > trailer > popular) kalau
                            // sumber pilihan lagi kosong.
                            val bannerList = when (heroSource) {
                                "HOT" -> data.hot
                                "POPULAR" -> data.popular
                                "RANDOM" -> data.random
                                else -> null
                            } ?: data.hot ?: data.trailer ?: data.popular ?: emptyList()

                            if (bannerList.isNotEmpty()) {
                                item {
                                    HeroBannerCarousel(
                                        bannerItems = bannerList.take(heroItemCount),
                                        style = heroStyle,
                                        autoplay = heroAutoplay,
                                        intervalMs = heroIntervalMs.toLong(),
                                        onAnimeClick = onAnimeClick
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            // Section: Sedang Tayang (Ongoing) -- di Dayynime v5, field
                            // "hot" merepresentasikan anime yang lagi tayang, sama seperti
                            // konvensi yang dipakai di Aniku. Sengaja ditaruh paling atas,
                            // di atas section "Baru Ditambahkan".
                            data.hot?.let { hotList ->
                                if (hotList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Sedang Tayang",
                                            items = hotList,
                                            onAnimeClick = onAnimeClick,
                                            onSeeAllClick = onSeeAllOngoingClick
                                        )
                                    }
                                }
                            }

                            // Section: Update Hari Ini (Today)
                            data.today?.let { todayList ->
                                if (todayList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Update Hari Ini",
                                            items = todayList,
                                            onAnimeClick = onAnimeClick
                                        )
                                    }
                                }
                            }

                            // Section: Baru Ditambahkan (New)
                            data.new?.let { newList ->
                                if (newList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Baru Ditambahkan",
                                            items = newList,
                                            onAnimeClick = onAnimeClick
                                        )
                                    }
                                }
                            }

                            // Section: Terpopuler (Popular)
                            data.popular?.let { popularList ->
                                if (popularList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Terpopuler",
                                            items = popularList,
                                            onAnimeClick = onAnimeClick
                                        )
                                    }
                                }
                            }

                            // Section: Rekomendasi (Random)
                            data.random?.let { randomList ->
                                if (randomList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Rekomendasi Pilihan",
                                            items = randomList,
                                            onAnimeClick = onAnimeClick
                                        )
                                    }
                                }
                            }

                            // Section: Segera Tayang (Waiting)
                            data.waiting?.let { waitingList ->
                                if (waitingList.isNotEmpty()) {
                                    item {
                                        AnimeHorizontalSection(
                                            title = "Segera Tayang",
                                            items = waitingList,
                                            onAnimeClick = onAnimeClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Compact Floating Scroll-Aware Header
            ZenimeHeader(
                isScrolled = isScrolled,
                transparentWhenTop = true,
                title = { ZenimeLogoTitle() },
                actions = {
                    ZenimeHeaderActionButton(
                        icon = Icons.Default.Search,
                        contentDescription = "Search Anime",
                        onClick = onSearchClick,
                        testTag = "home_search_button"
                    )
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun FullBleedHeroBannerCarousel(
    bannerItems: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    intervalMs: Long = 4500L
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(bannerItems, autoplay, intervalMs) {
        if (autoplay && bannerItems.size > 1) {
            while (true) {
                delay(intervalMs)
                currentIndex = (currentIndex + 1) % bannerItems.size
            }
        }
    }

    if (bannerItems.isEmpty()) return

    val currentAnime = bannerItems[currentIndex.coerceIn(0, bannerItems.lastIndex)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clickable { onAnimeClick(currentAnime.id) }
    ) {
        // Hero Image Cover Full Bleed
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(currentAnime.image_cover ?: currentAnime.image_poster)
                .crossfade(true)
                .build(),
            contentDescription = currentAnime.title ?: "Hero Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay vertically fading into #0B0E14
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF0B0E14).copy(alpha = 0.3f),
                            Color(0xFF0B0E14).copy(alpha = 0.85f),
                            Color(0xFF0B0E14)
                        )
                    )
                )
        )

        // Hero Info Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(0.72f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ZenimePrimary
                ) {
                    Text(
                        text = "TRENDING 🔥",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                currentAnime.type?.let { type ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentAnime.title ?: "Tanpa Judul",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Pager dots
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bannerItems.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (index == currentIndex) 20.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == currentIndex) ZenimePrimary
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }

        // Large Floating Circular Crimson Play Button on bottom right
        Surface(
            shape = CircleShape,
            color = ZenimePrimary,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .size(56.dp)
                .clickable { onAnimeClick(currentAnime.id) }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Mainkan Anime",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Gaya "Card Peek" -- tiap slide berupa kartu rounded yang gak penuh lebar
 * layar, dikit "ngintip" slide sebelahnya di kiri-kanan (gaya Disney+ /
 * Netflix "Coming Soon" row). Swipeable manual (HorizontalPager) SEKALIGUS
 * auto-advance, beda dari gaya Full Bleed yang cuma auto-advance tanpa swipe.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CardPeekHeroCarousel(
    bannerItems: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    intervalMs: Long = 4500L
) {
    if (bannerItems.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { bannerItems.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bannerItems, autoplay, intervalMs) {
        if (autoplay && bannerItems.size > 1) {
            while (true) {
                delay(intervalMs)
                val next = (pagerState.currentPage + 1) % bannerItems.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            val anime = bannerItems[page]
            // Kartu yang lagi di tengah tampil penuh; yang lagi "diintip"
            // di tepi sedikit diperkecil + diredupin, kasih kesan depth.
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val scale = 1f - (0.08f * kotlin.math.abs(pageOffset)).coerceIn(0f, 0.08f)
            val alpha = 1f - (0.35f * kotlin.math.abs(pageOffset)).coerceIn(0f, 0.35f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onAnimeClick(anime.id) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(anime.image_cover ?: anime.image_poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = anime.title ?: "Hero Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    anime.type?.let { type ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ZenimePrimary
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = anime.title ?: "Tanpa Judul",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            bannerItems.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (index == pagerState.currentPage) 20.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index == pagerState.currentPage) ZenimePrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

/**
 * Gaya "Minimal" -- strip compact, poster kecil + info ringkas, jauh lebih
 * pendek dari dua gaya lain. Buat user yang lebih mentingin cepet nyampe
 * ke daftar anime di bawahnya daripada banner gede yang makan tempat.
 */
@Composable
fun MinimalHeroCarousel(
    bannerItems: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    intervalMs: Long = 4500L
) {
    if (bannerItems.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(bannerItems, autoplay, intervalMs) {
        if (autoplay && bannerItems.size > 1) {
            while (true) {
                delay(intervalMs)
                currentIndex = (currentIndex + 1) % bannerItems.size
            }
        }
    }

    val currentAnime = bannerItems[currentIndex.coerceIn(0, bannerItems.lastIndex)]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAnimeClick(currentAnime.id) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(currentAnime.image_cover ?: currentAnime.image_poster)
                .crossfade(true)
                .build(),
            contentDescription = currentAnime.title ?: "Hero Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                currentAnime.type?.let { type ->
                    Text(
                        text = type.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZenimePrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = currentAnime.title ?: "Tanpa Judul",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = ZenimePrimary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Mainkan Anime",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Switcher gaya Hero Carousel -- baca preferensi "Gaya Tampilan" dari
 * Pengaturan (FULL_BLEED / CARD_PEEK / MINIMAL) dan render composable yang
 * sesuai. Satu titik masuk, dipanggil dari HomeScreen.
 */
@Composable
fun HeroBannerCarousel(
    bannerItems: List<AnimeItem>,
    style: String,
    autoplay: Boolean,
    intervalMs: Long,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (style) {
        "CARD_PEEK" -> CardPeekHeroCarousel(
            bannerItems = bannerItems,
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            autoplay = autoplay,
            intervalMs = intervalMs
        )
        "MINIMAL" -> MinimalHeroCarousel(
            bannerItems = bannerItems,
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            autoplay = autoplay,
            intervalMs = intervalMs
        )
        else -> FullBleedHeroBannerCarousel(
            bannerItems = bannerItems,
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            autoplay = autoplay,
            intervalMs = intervalMs
        )
    }
}

@Composable
fun AnimeHorizontalSection(
    title: String,
    items: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = title, onSeeAllClick = onSeeAllClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                AnimePosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime.id) }
                )
            }
        }
    }
}
