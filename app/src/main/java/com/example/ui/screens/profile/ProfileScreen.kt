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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.ui.components.GeneratedAvatar
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark
import com.example.ui.theme.ZenimeSurfaceVariantDark

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    firebaseUid: String,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onHistoryClick: (WatchHistoryEntity) -> Unit,
    onUpgradeClick: () -> Unit,
    onClanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val uniqueAnimeCount = uiState.history.distinctBy { it.animeId }.size

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
        containerColor = ZenimeBackgroundDark,
        topBar = {
            ZenimeHeader(title = { ZenimeScreenTitle(title = "Profil Saya") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // --- Banner + avatar overlap ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                ) {
                    if (backdropImage != null) {
                        AsyncImage(
                            model = backdropImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(ZenimeSurfaceVariantDark))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.35f), ZenimeBackgroundDark)
                                )
                            )
                    )
                }

                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(ZenimeBackgroundDark)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(ZenimeSurfaceDark)
                                .border(2.dp, ZenimePrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!uiState.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = uiState.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                GeneratedAvatar(
                                    seed = firebaseUid,
                                    label = uiState.username,
                                    size = 80.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.username.ifBlank { "Pengguna Zenime" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { viewModel.openEditDialog() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Profil",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (uiState.isPremium) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Image(
                                painter = painterResource(id = R.drawable.ic_premium_badge),
                                contentDescription = "Premium",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileStat(value = animatedFavoriteCount.toString(), label = "Favorit", modifier = Modifier.align(Alignment.Center).fillMaxWidth())
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileStat(value = animatedAnimeCount.toString(), label = "Anime Ditonton", modifier = Modifier.align(Alignment.Center).fillMaxWidth())
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileStat(value = animatedEpisodeCount.toString(), label = "Episode", modifier = Modifier.align(Alignment.Center).fillMaxWidth())
                        }
                    }
                }
            }

            // --- Entry point ke fitur Clan ---
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

            if (!uiState.isPremium) {
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
                            text = "Upgrade Premium buat upload foto profil & banner sendiri",
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

            // --- Favorite Shows ---
            SectionHeader(
                iconVector = Icons.Filled.Favorite,
                iconTint = ZenimePrimary,
                title = "Favorite Shows",
                trailing = if (uiState.favorites.isNotEmpty()) "${uiState.favorites.size} Anime" else null
            )

            if (uiState.favorites.isEmpty()) {
                EmptySectionBox(text = "Belum ada anime favorit.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.favorites, key = { it.id }) { favorite ->
                        FavoritePosterCard(
                            favorite = favorite,
                            onClick = { onAnimeClick(favorite.id) }
                        )
                    }
                }
            }

            // --- Riwayat Tontonan ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
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
                if (uiState.history.isNotEmpty()) {
                    IconButton(onClick = { showClearHistoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Hapus Riwayat",
                            tint = ZenimePrimary
                        )
                    }
                }
            }

            if (uiState.history.isEmpty()) {
                EmptySectionBox(text = "Belum ada riwayat tontonan.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.history, key = { it.animeId }) { item ->
                        HistoryPosterCard(item = item, onClick = onHistoryClick)
                    }
                }
            }

            // --- Tentang Zenime ---
            Spacer(modifier = Modifier.height(20.dp))
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
            onNonPremiumAvatarTap = { viewModel.notifyAvatarRequiresPremium() },
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

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
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

@Composable
private fun HistoryPosterCard(item: WatchHistoryEntity, onClick: (WatchHistoryEntity) -> Unit) {
    Card(
        modifier = Modifier.width(160.dp).clickable { onClick(item) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZenimeBackgroundDark)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(95.dp)) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.animeTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.episodeTitle ?: "",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
    onNonPremiumAvatarTap: () -> Unit,
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
                        .clickable {
                            if (isPremium) avatarPicker.launch("image/*") else onNonPremiumAvatarTap()
                        },
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
                        text = "Upload foto profil & banner khusus member Premium.",
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
