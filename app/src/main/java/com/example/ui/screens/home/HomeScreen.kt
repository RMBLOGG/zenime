package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.model.AnimeItem
import com.example.data.model.BacakomikListItem
import com.example.ui.components.AnimePosterCard
import com.example.ui.components.ComicPosterCard
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerBanner
import com.example.ui.components.ShimmerHorizontalSection
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.StarYellow
import com.example.ui.theme.ZenimeInfoBlue
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSeeAllOngoingClick: () -> Unit,
    onChatClick: () -> Unit,
    onPlayEpisodeClick: (episodeId: String, animeId: String) -> Unit,
    onComicClick: (String) -> Unit = {},
    onSeeAllComicClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onPremiumClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val comicLatestState by viewModel.comicLatestState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val heroStyle by viewModel.heroStyle.collectAsStateWithLifecycle()
    val heroAutoplay by viewModel.heroAutoplay.collectAsStateWithLifecycle()
    val heroIntervalMs by viewModel.heroIntervalMs.collectAsStateWithLifecycle()
    val heroItemCount by viewModel.heroItemCount.collectAsStateWithLifecycle()
    val heroSource by viewModel.heroSource.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        ShimmerBanner()
                        Spacer(modifier = Modifier.height(16.dp))
                        ShimmerHorizontalSection()
                        ShimmerHorizontalSection()
                    }
                }
                is Result.Error -> {
                    // Gagal narik Beranda (biasanya lagi offline). Kalau ada
                    // video yang udah didownload, jadiin Beranda tetap
                    // berguna -- tampilin itu di bawah pesan errornya,
                    // bukan cuma layar kosong nyuruh coba lagi.
                    if (downloads.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 16.dp)
                        ) {
                            ErrorStateView(
                                message = state.message,
                                onRetry = { viewModel.loadHome(forceConfigRefresh = true) }
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Coba Lagi",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = ZenimePrimary,
                                        modifier = Modifier.clickable {
                                            viewModel.loadHome(forceConfigRefresh = true)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            item {
                                Text(
                                    text = "Video yang sudah didownload",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(downloads, key = { it.episodeId }) { download ->
                                YoutubeStyleDownloadCard(
                                    item = download,
                                    onCardClick = {
                                        if (download.status == DownloadStatus.COMPLETED) {
                                            onPlayEpisodeClick(download.episodeId, download.animeId)
                                        }
                                    },
                                    onDeleteClick = { viewModel.deleteDownload(download.episodeId) }
                                )
                            }
                        }
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
                            // Kartu profil ala AniBiPlay -- avatar, username,
                            // zenime_code, status Premium (sisa hari), & saldo
                            // ZCoin. Header floating "Zenime" sudah dihapus,
                            // jadi cukup sedikit padding atas buat jarak status bar.
                            item {
                                HomeProfileHeader(
                                    state = profileState,
                                    onProfileClick = onProfileClick,
                                    onPremiumClick = onPremiumClick,
                                    onCoinClick = onCoinClick,
                                    onSearchClick = onSearchClick,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                HomePremiumBanner(
                                    isPremium = profileState.isPremium,
                                    onPremiumClick = onPremiumClick,
                                    onNotificationClick = onChatClick
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // "Terakhir Ditonton" -- continue watching row dihapus
                            // dari Beranda sesuai permintaan user.

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
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // Kartu promo "Diskusi Publik" -- persis di bawah hero
                            // carousel sesuai referensi, ngajak masuk Chat Global.
                            item {
                                HomeDiscussionPromoCard(onChatClick = onChatClick)
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Section donasi SociaBuzz -- diletakkan di bawah hero
                            // carousel biar keliatan tapi gak ganggu/nutupin
                            // konten atau nav bar kayak versi floating button.
                            item {
                                DonationSection(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
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
                                            title = "New Anime Update",
                                            items = newList,
                                            onAnimeClick = onAnimeClick,
                                            showNewBadge = true
                                        )
                                    }
                                }
                            }

                            // Section: Komik Terbaru -- sumber terpisah dari data
                            // anime (Result sendiri), jadi ditampilin selama ada
                            // isinya walau homeState anime masih loading/gagal.
                            val comicList = (comicLatestState as? Result.Success)?.data.orEmpty()
                            if (comicList.isNotEmpty()) {
                                item {
                                    ComicHorizontalSection(
                                        title = "Komik Terbaru",
                                        items = comicList,
                                        onComicClick = onComicClick,
                                        onSeeAllClick = onSeeAllComicClick
                                    )
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
        }
    }
}

/**
 * Wrapper "card" generik -- dipakai buat bungkus tiap section Beranda
 * (header profil, banner premium, hero, promo diskusi, & tiap row anime)
 * jadi kartu rounded terpisah, sesuai referensi desain baru: layout
 * "section-section yang dibungkus", bukan konten nempel polos di background.
 */
@Composable
private fun HomeSectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Modifier = Modifier.padding(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CardOutlineBorder.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Column(modifier = contentPadding, content = content)
    }
}

/**
 * Banner ajakan aktivasi Premium -- kartu terpisah persis di bawah kartu
 * profil, meniru referensi: ikon lonceng bulat di kiri + tombol pill besar
 * "AKTIFKAN PREMIUM DI SINI" yang makan sisa lebar kartu.
 */
@Composable
private fun HomePremiumBanner(
    isPremium: Boolean,
    onPremiumClick: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeSectionCard(
        modifier = modifier,
        contentPadding = Modifier.padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifikasi",
                    tint = ZenimePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ZenimeInfoBlue,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(onClick = onPremiumClick)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (isPremium) "KELOLA PREMIUM" else "BELI PREMIUM DI SINI",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Kartu promo "Diskusi Publik" -- ngajak user masuk ke Chat Global, posisinya
 * di bawah hero carousel sama kayak referensi (bar diskusi di bawah banner).
 */
@Composable
private fun HomeDiscussionPromoCard(
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeSectionCard(
        modifier = modifier,
        onClick = onChatClick,
        contentPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Diskusi Publik",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Buka Chat Global",
                tint = ZenimePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Kartu profil di paling atas Beranda, terinspirasi tampilan Home AniBiPlay:
 * avatar + username + zenime_code sebagai pengganti "#id", lalu dua chip di
 * bawahnya -- status Premium (gantiin "Level", isinya sisa hari aktif) dan
 * saldo ZCoin (gantiin "Crystal"/"AniGames"). Sekarang dibungkus jadi satu
 * kartu terpisah (bukan nempel polos di background) sesuai referensi baru.
 */
@Composable
private fun HomeProfileHeader(
    state: HomeProfileUiState,
    onProfileClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onCoinClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeSectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!state.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(state.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = state.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    GeneratedAvatar(
                        seed = state.zenimeCode ?: state.username,
                        label = state.username,
                        size = 48.dp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username.ifBlank { "Pengguna Zenime" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!state.zenimeCode.isNullOrBlank()) {
                    Text(
                        text = "#${state.zenimeCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onSearchClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari Anime",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = CardOutlineBorder.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Chip status Premium -- gantiin "Lvl. 1" di referensi, isinya
            // sisa hari aktif kalau lagi Premium, atau ajakan aktivasi kalau belum.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (state.isPremium) ZenimePrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPremiumClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = if (state.isPremium) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.isPremium && state.premiumDaysLeft != null -> "Sisa ${state.premiumDaysLeft} hari"
                            state.isPremium -> "Premium aktif"
                            else -> "Aktifkan Premium"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (state.isPremium) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chip saldo ZCoin -- gantiin tombol "AniGames" di referensi.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onCoinClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zcoin_badge),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatZCoinBalance(state.coinBalance),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "ZCoin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatZCoinBalance(balance: Long): String {
    return NumberFormat.getInstance(Locale("id", "ID")).format(balance)
}

/** Format angka mentah (String) jadi label ringkas "24.5K", null kalau kosong/invalid. */
private fun formatCountLabel(raw: String?): String? {
    val value = raw?.toLongOrNull() ?: return null
    return when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
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
    val heroShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(290.dp)
            .clip(heroShape)
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

        // Badge views (kiri atas), ala "24.5K views" di referensi.
        formatCountLabel(currentAnime.views)?.let { viewsLabel ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$viewsLabel views",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        // Badge ranking (#N), posisinya ngambang di atas judul ala referensi.
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text(
                text = "#${currentIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = StarYellow,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

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
 * Gaya "Crunchyroll" -- hero besar dengan judul, badge status/genre,
 * sinopsis ringkas, dan tombol CTA pill besar "Mulai Menonton" + tombol
 * bookmark bulat di sampingnya. Nyontek layout hero Crunchyroll: gambar di
 * atas transisi ke background solid di bawah (bukan gradient nutupin
 * gambar doang), teks & tombol duduk di area solid itu biar kebaca jelas.
 */
@Composable
fun CrunchyrollHeroCarousel(
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

    val anime = bannerItems[currentIndex.coerceIn(0, bannerItems.lastIndex)]
    var isBookmarked by remember(anime.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
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
            // Gradient nyambungin gambar ke background solid di bawah,
            // bukan sekedar gelapin gambar -- biar transisinya mulus kayak
            // referensi Crunchyroll.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            ),
                            startY = 0f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = anime.title ?: "Tanpa Judul",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Baris badge: status + tipe + genre pertama, dipisah titik --
            // persis pola "12+ • Sulih Suara | Takarir • Romansa, Fantasi"
            // di referensi, tapi pakai data yang beneran ada.
            Row(verticalAlignment = Alignment.CenterVertically) {
                anime.status?.let { status ->
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = ZenimePrimary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ZenimePrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                val metaLine = listOfNotNull(anime.type, anime.genre).joinToString(" • ")
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            anime.synopsis?.let { synopsis ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CTA row: tombol pill besar "Mulai Menonton" + tombol bookmark
            // bulat outline di sampingnya, sama kayak referensi.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ZenimePrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { onAnimeClick(anime.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mulai Menonton",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, ZenimePrimary),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { isBookmarked = !isBookmarked }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Simpan ke Daftar",
                            tint = ZenimePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                bannerItems.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .height(5.dp)
                            .width(if (index == currentIndex) 22.dp else 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == currentIndex) ZenimePrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Gaya "Dayynime" -- PEEK CAROUSEL (bukan single-card): card aktif hampir
 * penuh lebar, tapi sliver card berikutnya keliatan dikit di tepi kanan
 * (persis pola aslinya). Dot indicator bulat kecil.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DayynimeHeroCarousel(
    bannerItems: List<AnimeItem>,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    intervalMs: Long = 4500L
) {
    if (bannerItems.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { bannerItems.size })

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
        // contentPadding asimetris (start kecil, end besar) -- ini yang
        // bikin sliver card berikutnya keliatan di tepi kanan, sementara
        // card aktif nempel rata di kiri, persis referensi.
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 16.dp, end = 52.dp),
            pageSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) { page ->
            val anime = bannerItems[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
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
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Baris chip meta info (ikon + teks) -- persis pola
                    // "Episode X Episodes | 24 min | TV" di referensi, tapi
                    // pakai field yang emang tersedia dari API (type, time,
                    // status) daripada ngarang angka episode.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        anime.type?.let { type ->
                            DayynimeMetaChip(icon = Icons.Default.Tv, text = type)
                        }
                        anime.time?.let { time ->
                            DayynimeMetaChip(icon = Icons.Default.Schedule, text = time)
                        }
                        anime.status?.let { status ->
                            DayynimeMetaChip(icon = Icons.Default.FiberManualRecord, text = status)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dot indicator BULAT KECIL -- bukan bar panjang, sesuai referensi asli.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            bannerItems.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) ZenimePrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

/** Chip kecil ikon+teks buat baris meta info di [DayynimeHeroCarousel]. */
@Composable
private fun DayynimeMetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Switcher gaya Hero Carousel -- baca preferensi "Gaya Tampilan" dari
 * Pengaturan (FULL_BLEED / CRUNCHYROLL / DAYYNIME) dan render composable
 * yang sesuai. Satu titik masuk, dipanggil dari HomeScreen.
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
        "CRUNCHYROLL" -> CrunchyrollHeroCarousel(
            bannerItems = bannerItems,
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            autoplay = autoplay,
            intervalMs = intervalMs
        )
        "DAYYNIME" -> DayynimeHeroCarousel(
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
    showNewBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    HomeSectionCard(
        modifier = modifier.padding(vertical = 6.dp),
        contentPadding = Modifier.padding(vertical = 14.dp)
    ) {
        SectionHeader(
            title = title,
            onSeeAllClick = onSeeAllClick,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.id }) { anime ->
                Box {
                    AnimePosterCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime.id) }
                    )
                    if (showNewBadge) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ZenimeInfoBlue,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "New",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComicHorizontalSection(
    title: String,
    items: List<BacakomikListItem>,
    onComicClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    HomeSectionCard(
        modifier = modifier.padding(vertical = 6.dp),
        contentPadding = Modifier.padding(vertical = 14.dp)
    ) {
        SectionHeader(
            title = title,
            onSeeAllClick = onSeeAllClick,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.slug }) { comic ->
                ComicPosterCard(
                    comic = comic,
                    onClick = { onComicClick(comic.slug) },
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}

/**
 * Kartu video download bergaya feed YouTube -- thumbnail lebar penuh di
 * atas (bukan thumbnail kecil di samping kayak DownloadedEpisodeCard di
 * tab Koleksi), terus "avatar channel" bulat (posternya) + judul 2 baris +
 * baris meta di bawahnya. Dipakai khusus buat fallback download di
 * Beranda pas offline.
 */
@Composable
private fun YoutubeStyleDownloadCard(
    item: DownloadedEpisodeEntity,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        // Thumbnail full-width, persis proporsi thumbnail video di feed YouTube.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.episodeThumbnailUrl ?: item.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Chip status offline, pojok kiri atas -- kayak label "LIVE"/"4K" YouTube.
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "TERSIMPAN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Nomor episode, pojok kanan bawah -- posisi yang sama kayak
            // durasi video di thumbnail YouTube.
            if (!item.episodeIndex.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "EP ${item.episodeIndex}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // "Avatar channel" bulat, sama kayak logo channel di feed
            // YouTube. Prioritasin episodeThumbnailUrl (BUKAN posterUrl)
            // -- itu url yang sama dipakai buat thumbnail besar di atas,
            // yang kebukti kepakai offline karena udah ke-cache Coil pas
            // user nonton episode ini pertama kali. posterUrl anime belum
            // tentu pernah ke-load/ke-cache di device ini, jadi kalau
            // dipakai sendirian bisa nongol kosong pas offline. Icon di
            // belakang jadi fallback terakhir kalau dua-duanya gagal
            // dimuat (AsyncImage transparan pas gagal, jadi Icon-nya
            // tetap kelihatan, bukan bulet kosong item-item).
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.episodeThumbnailUrl ?: item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                val epLabel = if (!item.episodeTitle.isNullOrEmpty()) {
                    "${item.animeTitle} - ${item.episodeTitle}"
                } else {
                    "${item.animeTitle} - Episode ${item.episodeIndex ?: ""}"
                }
                Text(
                    text = epLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.animeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Baris meta -- posisinya sama kayak "views • waktu upload"
                // di YouTube.
                Text(
                    text = when (item.status) {
                        DownloadStatus.COMPLETED -> "Siap ditonton offline • ${formatDownloadSize(item.totalBytes)}"
                        DownloadStatus.FAILED -> "Download gagal"
                        else -> "Mendownload..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == DownloadStatus.FAILED) {
                        Color(0xFFE57373)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opsi lainnya",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Hapus dari download") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

private fun formatDownloadSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.0f MB".format(mb)
    }
}
