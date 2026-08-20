package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle

// ---------------------------------------------------------------------------
// Building blocks pengaturan -- satu card per section, baris dipisah divider
// tipis (bukan spacer gede), ikon dikasih "chip" kotak membulat bertinta
// warna primary. Polanya dipakai konsisten di semua section di bawah.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardOutlineBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsIconChip(
    icon: ImageVector,
    tint: Color = ZenimePrimary
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            SettingsIconChip(icon = icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

/** Divider tipis, indent-nya sejajar sama teks (ngelewatin ikon chip). */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 1.dp,
        color = CardOutlineBorder.copy(alpha = 0.6f)
    )
}

/**
 * Mini ilustrasi gaya carousel (bukan foto asli, cuma bentuk kotak-kotak
 * sederhana) -- biar user langsung kebayang bedanya "Full Bleed" vs "Card
 * Peek" vs "Minimal" tanpa harus buka Beranda dulu buat coba-coba.
 */
@Composable
private fun HeroStylePreviewThumb(style: String, selected: Boolean) {
    val borderColor = if (selected) ZenimePrimary else CardOutlineBorder
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        when (style) {
            "CRUNCHYROLL" -> Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ZenimePrimary.copy(alpha = 0.7f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ZenimePrimary)
                )
            }
            "DAYYNIME" -> Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(ZenimePrimary.copy(alpha = 0.7f))
                )
                Box(
                    modifier = Modifier
                        .weight(0.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(ZenimePrimary.copy(alpha = 0.3f))
                )
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ZenimePrimary.copy(alpha = 0.7f))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPremiumClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val defaultQuality by viewModel.defaultQuality.collectAsStateWithLifecycle()
    val autoSkipIntro by viewModel.autoSkipIntro.collectAsStateWithLifecycle()
    val autoSkipOutro by viewModel.autoSkipOutro.collectAsStateWithLifecycle()
    val heroStyle by viewModel.heroStyle.collectAsStateWithLifecycle()
    val heroAutoplay by viewModel.heroAutoplay.collectAsStateWithLifecycle()
    val heroIntervalMs by viewModel.heroIntervalMs.collectAsStateWithLifecycle()
    val heroItemCount by viewModel.heroItemCount.collectAsStateWithLifecycle()
    val heroSource by viewModel.heroSource.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }
    var showItemCountMenu by remember { mutableStateOf(false) }

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
                title = { ZenimeScreenTitle(title = "Pengaturan") }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {

                // ---------------- Section: Akun ----------------
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel("Akun")

                    val user = currentUser
                    val glowBrush = Brush.radialGradient(
                        colors = listOf(ZenimePrimary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 420f
                    )

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardOutlineBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .background(glowBrush)
                                .padding(18.dp)
                        ) {
                            if (user == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(ZenimePrimary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = ZenimePrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            "Belum Login",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Login buat sinkronin pengaturan kamu",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = { viewModel.signInWithGoogle(context) },
                                    enabled = !isSigningIn,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.dp, CardOutlineBorder)
                                ) {
                                    if (isSigningIn) {
                                        CircularProgressIndicator(
                                            color = ZenimePrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Menghubungkan...")
                                    } else {
                                        Text("G", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Masuk dengan Google")
                                    }
                                }

                                if (loginError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = loginError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val photoUrl = user.photoUrl?.toString()
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(ZenimePrimary, ZenimePrimary.copy(alpha = 0.35f))
                                                ),
                                                shape = CircleShape
                                            )
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!photoUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = user.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                tint = ZenimePrimary,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName ?: "Pengguna Zenime",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = user.email ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { showSignOutConfirm = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = "Keluar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = CardOutlineBorder.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onPremiumClick)
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SettingsIconChip(icon = Icons.Default.Star)
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                "Premium",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                "Lihat paket & aktifkan Premium",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------- Section: Tampilan & Tema ----------------
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel("Tampilan & Tema")

                    SettingsGroupCard {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingsIconChip(icon = Icons.Default.Palette)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Mode Tema", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            listOf(
                                "DARK" to "Tema Gelap (Default)",
                                "LIGHT" to "Tema Terang",
                                "SYSTEM" to "Ikuti Sistem"
                            ).forEach { (key, label) ->
                                val selected = themeMode == key
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) ZenimePrimary.copy(alpha = 0.08f) else Color.Transparent
                                        )
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.setThemeMode(key) },
                                        colors = RadioButtonDefaults.colors(selectedColor = ZenimePrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.ColorLens,
                            title = "Dynamic Color",
                            subtitle = "Warna dari Wallpaper (Android 12+)",
                            trailing = {
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ZenimePrimary
                                    )
                                )
                            }
                        )
                    }
                }

                // ---------------- Section: Pemutaran Video ----------------
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel("Pemutaran Video")

                    SettingsGroupCard {
                        SettingsRow(
                            icon = Icons.Default.HighQuality,
                            title = "Kualitas Video Default",
                            subtitle = "Kualitas utama saat memuat episode",
                            trailing = {
                                Box {
                                    OutlinedCard(
                                        onClick = { showQualityMenu = true },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, CardOutlineBorder)
                                    ) {
                                        Text(
                                            text = defaultQuality,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = ZenimePrimary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showQualityMenu,
                                        onDismissRequest = { showQualityMenu = false }
                                    ) {
                                        listOf("1080p", "720p", "480p", "360p").forEach { q ->
                                            DropdownMenuItem(
                                                text = { Text(q, fontWeight = if (q == defaultQuality) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    viewModel.setDefaultQuality(q)
                                                    showQualityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.FastForward,
                            title = "Lewati Intro Otomatis",
                            subtitle = "Lompat ke detik 90 pas episode dibuka dari awal",
                            trailing = {
                                Switch(
                                    checked = autoSkipIntro,
                                    onCheckedChange = { viewModel.setAutoSkipIntro(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ZenimePrimary
                                    )
                                )
                            }
                        )

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.SkipNext,
                            title = "Auto-Lanjut Episode (Outro)",
                            subtitle = "Lanjut ke episode berikutnya otomatis pas mepet abis",
                            trailing = {
                                Switch(
                                    checked = autoSkipOutro,
                                    onCheckedChange = { viewModel.setAutoSkipOutro(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ZenimePrimary
                                    )
                                )
                            }
                        )
                    }
                }

                // ---------------- Section: Tampilan Beranda (Hero Carousel) ----------------
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel("Tampilan Beranda")

                    SettingsGroupCard {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingsIconChip(icon = Icons.Default.ViewCarousel)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Gaya Hero Carousel",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        "Layout banner unggulan di paling atas Beranda",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            listOf(
                                Triple("FULL_BLEED", "Full Bleed (Default)", "Banner besar penuh layar, info di bawah"),
                                Triple("CRUNCHYROLL", "Crunchyroll Style", "Sinopsis + tombol \"Mulai Menonton\" & bookmark"),
                                Triple("DAYYNIME", "Dayynime Style", "Peek carousel dengan chip info, bisa digeser")
                            ).forEach { (key, label, desc) ->
                                val selected = heroStyle == key
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) ZenimePrimary.copy(alpha = 0.08f) else Color.Transparent
                                        )
                                        .clickable { viewModel.setHeroStyle(key) }
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    // Mini preview visual per gaya -- biar user
                                    // gak cuma nebak dari nama doang, kelihatan
                                    // langsung bedanya gimana.
                                    HeroStylePreviewThumb(style = key, selected = selected)

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            color = if (selected) ZenimePrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.setHeroStyle(key) },
                                        colors = RadioButtonDefaults.colors(selectedColor = ZenimePrimary)
                                    )
                                }
                            }
                        }

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.Autorenew,
                            title = "Auto-Slide Otomatis",
                            subtitle = "Geser banner sendiri tanpa disentuh",
                            trailing = {
                                Switch(
                                    checked = heroAutoplay,
                                    onCheckedChange = { viewModel.setHeroAutoplay(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ZenimePrimary
                                    )
                                )
                            }
                        )

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.Timer,
                            title = "Kecepatan Slide",
                            subtitle = if (heroAutoplay) "Jeda antar pergantian banner" else "Aktifkan Auto-Slide dulu",
                            trailing = {
                                Box {
                                    OutlinedCard(
                                        onClick = { if (heroAutoplay) showIntervalMenu = true },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, CardOutlineBorder)
                                    ) {
                                        Text(
                                            text = "${heroIntervalMs / 1000f}".removeSuffix(".0") + "s",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = if (heroAutoplay) ZenimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showIntervalMenu,
                                        onDismissRequest = { showIntervalMenu = false }
                                    ) {
                                        listOf(3000, 4500, 6000, 8000).forEach { ms ->
                                            val label = "${ms / 1000f}".removeSuffix(".0") + " Detik"
                                            DropdownMenuItem(
                                                text = { Text(label, fontWeight = if (ms == heroIntervalMs) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    viewModel.setHeroIntervalMs(ms)
                                                    showIntervalMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        SettingsRowDivider()

                        SettingsRow(
                            icon = Icons.Default.FormatListNumbered,
                            title = "Jumlah Anime di Carousel",
                            subtitle = "Berapa judul unggulan yang ditampilin",
                            trailing = {
                                Box {
                                    OutlinedCard(
                                        onClick = { showItemCountMenu = true },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = BorderStroke(1.dp, CardOutlineBorder)
                                    ) {
                                        Text(
                                            text = "$heroItemCount",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = ZenimePrimary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showItemCountMenu,
                                        onDismissRequest = { showItemCountMenu = false }
                                    ) {
                                        listOf(3, 4, 5, 6, 8).forEach { count ->
                                            DropdownMenuItem(
                                                text = { Text("$count Anime", fontWeight = if (count == heroItemCount) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    viewModel.setHeroItemCount(count)
                                                    showItemCountMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        SettingsRowDivider()

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingsIconChip(icon = Icons.Default.Source)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Sumber Banner", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            listOf(
                                "AUTO" to "Otomatis (Rekomendasi Sistem)",
                                "HOT" to "Sedang Tayang",
                                "POPULAR" to "Terpopuler",
                                "RANDOM" to "Rekomendasi Pilihan"
                            ).forEach { (key, label) ->
                                val selected = heroSource == key
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) ZenimePrimary.copy(alpha = 0.08f) else Color.Transparent
                                        )
                                        .clickable { viewModel.setHeroSource(key) }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.setHeroSource(key) },
                                        colors = RadioButtonDefaults.colors(selectedColor = ZenimePrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------- Section: Tentang Aplikasi ----------------
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGroupLabel("Tentang Aplikasi")

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardOutlineBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            // Header: logo di kiri, nama + tagline di tengah, versi di kanan.
                            // Row biar gak ada masalah centering kayak layout stack sebelumnya.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.zenime_logo_1786121211149),
                                    contentDescription = "Zenime Logo",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Zenime",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.2).sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Nonton Anime, Tenang & Modern",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(ZenimePrimary.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "v1.0.0",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ZenimePrimary
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 1.dp, color = CardOutlineBorder.copy(alpha = 0.6f))

                            // Baris info teknis, rata kiri konsisten sama section lain.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Native Kotlin \u00b7 Jetpack Compose \u00b7 Material 3",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(ZenimePrimary.copy(alpha = 0.7f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Powered by Dayynime v5 API & Direct MP4 Streaming",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZenimePrimary.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(94.dp))
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Keluar dari Akun?") },
            text = { Text("Kamu bisa login lagi kapan aja lewat halaman ini.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        showSignOutConfirm = false
                    }
                ) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
