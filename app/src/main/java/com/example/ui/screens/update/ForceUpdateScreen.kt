package com.example.ui.screens.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeOnSurfaceVariantDark
import com.example.util.DownloadState

/**
 * Layar SATU-SATUNYA yang ditampilkan kalau ada release GitHub lebih baru
 * dari versionName APK yang jalan. Sengaja TIDAK ada tombol back/skip --
 * ini pengganti total konten app (lihat MainActivity: kalau needsUpdate
 * true, ZenimeAppNavHost sama sekali gak di-compose).
 *
 * Desain ngikutin identitas visual Zenime yang udah ada di tempat lain
 * (aksen crimson #E4344A di atas navy gelap, tombol pill penuh) -- BUKAN
 * ikon di dalam lingkaran solid kayak versi sebelumnya. Logo ditaruh polos
 * dengan glow tipis di belakangnya, dan info versi ditampilin sebagai chip
 * "v{current} -> v{latest}" (informasi nyata, bukan hiasan).
 *
 * [downloadState] datang dari ApkDownloader di MainActivity dan nentuin
 * tombol mana yang tampil:
 * - Idle       -> "Update Sekarang" (mulai download, [onDownloadClick])
 * - Downloading -> progress bar + persen
 * - Downloaded -> "Install Sekarang" (buka installer sistem, [onInstallClick])
 * - Failed     -> pesan error + "Coba Lagi" ([onRetryClick])
 */
@Composable
fun ForceUpdateScreen(
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String,
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Logo polos dengan glow tipis di belakangnya -- bukan lingkaran
        // solid yang nutupin bentuk asli logo.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
            )
            Image(
                painter = painterResource(id = R.drawable.zenime_logo_1786121211149),
                contentDescription = "Zenime Logo",
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "PEMBARUAN WAJIB",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Update Tersedia",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Chip versi: informasi nyata (versi sekarang -> versi terbaru),
        // bukan sekadar dekorasi.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, CardOutlineBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "v$currentVersion",
                style = MaterialTheme.typography.labelLarge,
                color = ZenimeOnSurfaceVariantDark
            )
            Text(
                text = "  →  ",
                style = MaterialTheme.typography.labelLarge,
                color = ZenimeOnSurfaceVariantDark
            )
            Text(
                text = "v$latestVersion",
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = when (downloadState) {
                is DownloadState.Failed -> downloadState.message
                else -> "Aplikasi tidak bisa dipakai sebelum diperbarui ke versi terbaru."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = ZenimeOnSurfaceVariantDark,
            textAlign = TextAlign.Center
        )

        if (releaseNotes.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            ChangelogCard(releaseNotes = releaseNotes)
        }

        Spacer(modifier = Modifier.height(28.dp))

        when (downloadState) {
            is DownloadState.Idle -> {
                UpdateButton(text = "Update Sekarang", accent = accent, onClick = onDownloadClick)
            }
            is DownloadState.Downloading -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = accent,
                        trackColor = CardOutlineBorder
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Mengunduh… ${downloadState.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZenimeOnSurfaceVariantDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            is DownloadState.Downloaded -> {
                UpdateButton(text = "Install Sekarang", accent = accent, onClick = onInstallClick)
            }
            is DownloadState.Failed -> {
                UpdateButton(text = "Coba Lagi", accent = accent, onClick = onRetryClick)
            }
        }

        Spacer(modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun UpdateButton(text: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(999.dp),
        color = accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Changelog dari release body GitHub, expandable. Cuma parsing minimal
 * (heading "##"/"###", bullet "- ", bold "**", divider "---") -- cukup
 * buat release notes yang ditulis manual, bukan full markdown renderer.
 */
@Composable
private fun ChangelogCard(releaseNotes: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardOutlineBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lihat perubahan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ZenimeOnSurfaceVariantDark,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(start = 16.dp, end = 16.dp, bottom = 14.dp))
                ) {
                    releaseNotes.lines().forEach { line ->
                        when {
                            line.startsWith("### ") || line.startsWith("## ") -> {
                                Text(
                                    text = line.removePrefix("### ").removePrefix("## "),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            line.startsWith("- ") -> {
                                Row(modifier = Modifier.padding(bottom = 2.dp)) {
                                    Text(
                                        text = "•  ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = line.removePrefix("- ").replace("**", ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ZenimeOnSurfaceVariantDark
                                    )
                                }
                            }
                            line.startsWith("---") -> {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                            else -> {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ZenimeOnSurfaceVariantDark,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
