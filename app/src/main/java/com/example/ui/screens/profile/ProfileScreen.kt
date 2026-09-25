package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.ui.components.ClanRainbowBadge
import com.example.ui.components.GeneratedAvatar
import com.example.ui.components.LevelBadge
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimeInfoBlue
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark
import com.example.ui.theme.ZenimeSurfaceVariantDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profil Saya -- dirombak total ngikutin pola Wibuku: banner full-bleed jadi
 * satu kesatuan sama avatar (bukan app bar solid terpisah), identitas +
 * badge level/premium ngambang di bawah avatar, stat row flat 4 kolom pake
 * garis pemisah (bukan kartu kotak), tombol pill sekunder (Clan/Leaderboard),
 * CTA utama full-width, terus tab "Semua / Favorit / Riwayat" persis posisi
 * tab "Semua / Komentar / Riwayat" di Wibuku.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    firebaseUid: String,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onHistoryClick: (WatchHistoryEntity) -> Unit,
    onUpgradeClick: () -> Unit,
    onClanClick: () -> Unit,
    onXpLeaderboardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val uniqueAnimeCount = uiState.history.distinctBy { it.animeId }.size

    // Instance yang sama persis dipakai MyXpCard di bawah (key sama), jadi
    // gak dobel network call -- cuma numpang ambil angka Level buat masuk
    // ke stat row atas, kayak susunan 4 angka di profil Wibuku.
    val xpViewModel: MyXpViewModel = viewModel(
        key = "my_xp_$firebaseUid",
        factory = viewModelFactory { initializer { MyXpViewModel(firebaseUid) } }
    )
    val xpUiState by xpViewModel.uiState.collectAsStateWithLifecycle()
    val level = xpUiState.userXp?.level ?: 1

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    val animatedFavoriteCount by animateIntAsState(
        targetValue = if (startAnimation) uiState.favorites.size else 0,
        animationSpec = tween(1200),
        label = "favorite_count"
    )
    val animatedAnimeCount by animateIntAsState(
        targetValue = if (startAnimation) uniqueAnimeCount else 0,
        animationSpec = tween(1200),
        label = "anime_count"
    )
    val animatedEpisodeCount by animateIntAsState(
        targetValue = if (startAnimation) uiState.history.size else 0,
        animationSpec = tween(1200),
        label = "episode_count"
    )

    // Banner: prioritas foto custom upload (khusus Premium), fallback ke poster
    // favorit/riwayat pertama (kayak Kuroflix), fallback terakhir warna solid.
    val backdropImage = uiState.bannerUrl
        ?: uiState.favorites.firstOrNull()?.posterUrl
        ?: uiState.history.firstOrNull()?.posterUrl

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ZenimeBackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // --- Hero section: banner nutupin avatar + identitas + stat + CTA,
            // ukurannya ngikutin tinggi konten di dalemnya (persis Wibuku) ---
            ProfileHeroSection(
                backdropImage = backdropImage,
                avatarUrl = uiState.avatarUrl,
                firebaseUid = firebaseUid,
                username = uiState.username.ifBlank { "Pengguna Zenime" },
                userNumber = uiState.userNumber,
                isPremium = uiState.isPremium,
                clanTag = uiState.clanTag,
                level = level,
                stats = listOf(
                    animatedFavoriteCount.toString() to "Favorit",
                    animatedAnimeCount.toString() to "Anime Ditonton",
                    animatedEpisodeCount.toString() to "Episode",
                    level.toString() to "Level"
                ),
                onBackClick = onBackClick,
                onClanClick = onClanClick,
                onLeaderboardClick = onXpLeaderboardClick,
                onEditClick = { viewModel.openEditDialog() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Tab: Semua / Favorit / Riwayat ---
            ProfileTabRow(selectedIndex = selectedTab, onSelect = { selectedTab = it })

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> ProfileSemuaTab(
                    firebaseUid = firebaseUid,
                    isPremium = uiState.isPremium,
                    onClanClick = onClanClick,
                    onUpgradeClick = onUpgradeClick,
                    onXpLeaderboardClick = onXpLeaderboardClick
                )

                1 -> ProfileFavoritTab(
                    favorites = uiState.favorites,
                    onAnimeClick = onAnimeClick
                )

                else -> ProfileRiwayatTab(
                    history = uiState.history,
                    username = uiState.username.ifBlank { "Pengguna Zenime" },
                    avatarUrl = uiState.avatarUrl,
                    firebaseUid = firebaseUid,
                    isPremium = uiState.isPremium,
                    onHistoryClick = onHistoryClick,
                    onClearAllClick = { showClearHistoryDialog = true }
                )
            }
        }
    }

    if (uiState.isEditDialogOpen) {
        val context = LocalContext.current
        EditProfileDialog(
            currentUsername = uiState.username,
            currentAvatarUrl = uiState.avatarUrl,
            currentBannerUrl = uiState.bannerUrl,
            avatarSeed = firebaseUid,
            isPremium = uiState.isPremium,
            isSavingUsername = uiState.isSavingUsername,
            isUploadingAvatar = uiState.isUploadingAvatar,
            isUploadingBanner = uiState.isUploadingBanner,
            errorMessage = uiState.editError,
            onPickAvatar = { uri -> viewModel.uploadAvatar(context, uri) },
            onPickBanner = { uri -> viewModel.uploadBanner(context, uri) },
            onNonPremiumBannerTap = { viewModel.notifyBannerRequiresPremium() },
            onSaveUsername = { newName -> viewModel.saveUsername(newName) },
            onDismiss = { viewModel.closeEditDialog() }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = ZenimeSurfaceDark,
            title = { Text("Hapus Semua Riwayat?", color = Color.White) },
            text = {
                Text(
                    "Tindakan ini gak bisa dibatalin. Seluruh riwayat tontonan lokal kamu bakal dihapus permanen.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Hapus", color = ZenimePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Batal", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

/** Banner full-bleed + avatar overlap plain-border, ala kartu profil Wibuku. */
/**
 * Hero section ala Wibuku: SATU banner yang nutupin avatar, nama, badge,
 * stat, pill sekunder, sampe tombol CTA -- gambar banner otomatis ngikutin
 * tinggi konten di dalemnya (pake matchParentSize), bukan kotak setinggi
 * fix yang keputus sebelum konten identitas.
 */
@Composable
private fun ProfileHeroSection(
    backdropImage: String?,
    avatarUrl: String?,
    firebaseUid: String,
    username: String,
    userNumber: Long?,
    isPremium: Boolean,
    clanTag: String?,
    level: Int,
    stats: List<Pair<String, String>>,
    onBackClick: () -> Unit,
    onClanClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Background banner, ukurannya nempel persis ke Column konten di bawah.
        if (backdropImage != null) {
            AsyncImage(
                model = backdropImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = 14.dp)
            )
        } else {
            Box(modifier = Modifier.matchParentSize().background(ZenimeSurfaceVariantDark))
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.6f),
                            ZenimeBackgroundDark
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 20.dp)
        ) {
            // Avatar plain, ga ada border/ring sama sekali.
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape).background(ZenimeSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    GeneratedAvatar(seed = firebaseUid, label = username, size = 120.dp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = username,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (userNumber != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Text(
                            text = "ID #$userNumber",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isPremium) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Image(
                                painter = painterResource(id = R.drawable.ic_verified_badge),
                                contentDescription = "Verified",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(level = level)
                if (!clanTag.isNullOrBlank()) {
                    ClanRainbowBadge(text = clanTag)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stat row flat -- angka gede, label kecil di bawah, garis pemisah tipis.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                stats.forEachIndexed { index, (value, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (index != stats.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dua tombol pill sekunder, peran kayak "WIBUxNAKAMA" / "Lihat Pet".
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClanClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clan", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, ZenimePrimary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ZenimePrimary)
                ) {
                    Icon(Icons.Filled.Leaderboard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Leaderboard", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CTA utama full-width, posisi & bobot visual kayak "Tambah Teman".
            Button(
                onClick = onEditClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profil", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp, start = 8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PillBadge(icon: ImageVector, text: String, containerColor: Color, contentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Tab "Semua / Favorit / Riwayat" -- posisi & garis indikator persis tab Wibuku. */
@Composable
private fun ProfileTabRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Semua", "Favorit", "Riwayat")
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(if (selected) 28.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) ZenimePrimary else Color.Transparent)
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardOutlineBorder))
    }
}

/** Isi tab "Semua": kartu Level/XP, entry Clan, upsell Premium, dan Tentang Zenime. */
@Composable
private fun ProfileSemuaTab(
    firebaseUid: String,
    isPremium: Boolean,
    onClanClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onXpLeaderboardClick: () -> Unit
) {
    Column {
        MyXpCard(firebaseUid = firebaseUid, onLeaderboardClick = onXpLeaderboardClick)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onClanClick),
            colors = CardDefaults.cardColors(containerColor = ZenimeSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gabung atau bikin Clan bareng sesama penonton",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lihat",
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (!isPremium) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = onUpgradeClick),
                colors = CardDefaults.cardColors(containerColor = ZenimeSurfaceDark),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ZenimePrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Upgrade Premium buat upload banner profil sendiri",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lihat",
                        color = ZenimePrimary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ZenimeSurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardOutlineBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = ZenimePrimary)
                    Text(
                        text = "Tentang Zenime",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Zenime adalah aplikasi streaming anime premium buat nonton ribuan judul favoritmu langsung dari HP Android, lengkap dengan Chat Global & fitur Komik.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/** Isi tab "Favorit": grid poster anime favorit. */
@Composable
private fun ProfileFavoritTab(favorites: List<FavoriteEntity>, onAnimeClick: (String) -> Unit) {
    Column {
        SectionHeader(
            iconVector = Icons.Filled.Favorite,
            iconTint = ZenimePrimary,
            title = "Favorite Shows",
            trailing = if (favorites.isNotEmpty()) "${favorites.size} Anime" else null
        )
        if (favorites.isEmpty()) {
            EmptySectionBox(text = "Belum ada anime favorit.")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoritePosterCard(favorite = favorite, onClick = { onAnimeClick(favorite.id) })
                }
            }
        }
    }
}

/** Isi tab "Riwayat": feed flat kayak Wibuku -- avatar+nama+waktu, thumbnail+judul, progress bar. Ga dibungkus card. */
@Composable
private fun ProfileRiwayatTab(
    history: List<WatchHistoryEntity>,
    username: String,
    avatarUrl: String?,
    firebaseUid: String,
    isPremium: Boolean,
    onHistoryClick: (WatchHistoryEntity) -> Unit,
    onClearAllClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Riwayat",
                    tint = ZenimePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Riwayat Tontonan",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearAllClick) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Hapus Riwayat",
                        tint = ZenimePrimary
                    )
                }
            }
        }

        if (history.isEmpty()) {
            EmptySectionBox(text = "Belum ada riwayat tontonan.")
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                history.forEachIndexed { index, item ->
                    HistoryRow(
                        item = item,
                        username = username,
                        avatarUrl = avatarUrl,
                        firebaseUid = firebaseUid,
                        isPremium = isPremium,
                        onClick = onHistoryClick
                    )
                    if (index != history.lastIndex) {
                        Spacer(modifier = Modifier.height(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    trailing: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(text = title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (trailing != null) {
            Text(text = trailing, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptySectionBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZenimeSurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FavoritePosterCard(favorite: FavoriteEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(120.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZenimeBackgroundDark)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                AsyncImage(
                    model = favorite.posterUrl,
                    contentDescription = favorite.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = favorite.title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * Baris riwayat tontonan flat kayak feed Wibuku -- avatar mini + nama + waktu
 * relatif di atas, thumbnail + judul/episode, terus play icon + progress bar
 * + label waktu (mm:ss / mm:ss) di bawah. Ga dibungkus Card/kotak sama sekali.
 */
@Composable
private fun HistoryRow(
    item: WatchHistoryEntity,
    username: String,
    avatarUrl: String?,
    firebaseUid: String,
    isPremium: Boolean,
    onClick: (WatchHistoryEntity) -> Unit
) {
    val progressFraction = if (item.durationMs > 0) {
        (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth().clickable { onClick(item) }) {
        // --- Baris atas: avatar mini + nama + badge verified, waktu di kanan ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(ZenimeSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    GeneratedAvatar(seed = firebaseUid, label = username, size = 28.dp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = username,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isPremium) {
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_verified_badge),
                    contentDescription = "Verified",
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatRelativeTime(item.lastUpdated),
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Thumbnail + judul/episode ---
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    text = item.animeTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = item.episodeTitle,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Play icon + progress bar + label waktu, full width ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Lanjut nonton",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ZenimePrimary,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${formatWatchDuration(item.progressMs)} / ${formatWatchDuration(item.durationMs)}",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/** Format durasi ms jadi mm:ss (atau h:mm:ss kalau udah lewat 1 jam), ala label progress Wibuku. */
private fun formatWatchDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/** Waktu relatif ala feed Wibuku ("X jam lalu" / "X hari lalu"), fallback tanggal kalau udah lama. */
private fun formatRelativeTime(ms: Long): String {
    val diff = (System.currentTimeMillis() - ms).coerceAtLeast(0)
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit lalu"
        hours < 24 -> "$hours jam lalu"
        days < 30 -> "$days hari lalu"
        else -> SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(ms))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    currentUsername: String,
    currentAvatarUrl: String?,
    currentBannerUrl: String?,
    avatarSeed: String,
    isPremium: Boolean,
    isSavingUsername: Boolean,
    isUploadingAvatar: Boolean,
    isUploadingBanner: Boolean,
    errorMessage: String?,
    onPickAvatar: (Uri) -> Unit,
    onPickBanner: (Uri) -> Unit,
    onNonPremiumBannerTap: () -> Unit,
    onSaveUsername: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var usernameInput by remember { mutableStateOf(currentUsername) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onPickAvatar(uri) }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onPickBanner(uri) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ZenimeSurfaceDark)
                .border(1.dp, CardOutlineBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Edit Profil",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // --- Banner ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZenimeBackgroundDark)
                        .border(1.dp, CardOutlineBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            if (isPremium) bannerPicker.launch("image/*") else onNonPremiumBannerTap()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentBannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentBannerUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(
                            text = "Ketuk buat pasang banner",
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (isUploadingBanner) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(ZenimePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Ganti banner",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // --- Avatar ---
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(ZenimeBackgroundDark)
                        .border(1.dp, CardOutlineBorder, CircleShape)
                        .clickable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        GeneratedAvatar(seed = avatarSeed, label = currentUsername, size = 84.dp)
                    }

                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(ZenimePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Ganti foto profil",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (!isPremium) {
                    Text(
                        text = "Upload banner profil khusus member Premium.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { if (it.length <= 24) usernameInput = it },
                    label = { Text("Username", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ZenimePrimary,
                        unfocusedBorderColor = CardOutlineBorder,
                        cursorColor = ZenimePrimary
                    )
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = ZenimePrimary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CardOutlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Tutup")
                    }
                    Button(
                        onClick = { onSaveUsername(usernameInput) },
                        modifier = Modifier.weight(1f),
                        enabled = !isSavingUsername,
                        colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
                    ) {
                        if (isSavingUsername) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}
