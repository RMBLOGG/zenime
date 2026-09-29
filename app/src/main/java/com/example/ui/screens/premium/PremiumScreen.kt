package com.example.ui.screens.premium

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.api.SupabaseConfig
import com.example.data.model.PremiumPackage
import com.example.ui.components.CheckoutSheet
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.StatusOngoing
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimePrimaryContainer

/** Emas badge Premium -- dipakai buat semua penanda "ini fitur Premium" di halaman ini. */
private val PremiumGold = Color(0xFFF2C46D)

@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Harga per bulan tertinggi (biasanya paket 1 bulan) = patokan buat hitung % hemat.
    val basePerMonth = remember(uiState.packages) {
        uiState.packages
            .mapNotNull { p -> packageMonths(p)?.let { m -> p.price.toDouble() / m } }
            .maxOrNull()
    }

    val context = LocalContext.current
    uiState.selectedPackage?.let { pkg ->
        CheckoutSheet(
            title = "Paket ${pkg.label}",
            priceText = "Rp ${formatRupiah(pkg.price)}",
            isLoadingCode = uiState.isLoadingCode,
            zenimeCode = uiState.zenimeCode,
            codeError = uiState.codeError,
            onRetryCode = viewModel::retryLoadCode,
            onPayQris = {
                uiState.zenimeCode?.let { code ->
                    openCheckout(context, SupabaseConfig.STOREFRONT_URL, code, pkg.id)
                }
            },
            onPayManual = {
                // Buat pembeli yang QRIS otomatisnya gak kebaca e-wallet/bank
                // mereka (mis. luar negeri) -- verifikasi dilakukan manual admin.
                uiState.zenimeCode?.let { code ->
                    openCheckout(context, SupabaseConfig.MANUAL_STOREFRONT_URL, code, pkg.id)
                }
            },
            onDismiss = viewModel::clearSelection
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_premium_badge),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
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
                title = { ZenimeScreenTitle(title = "Premium") }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PremiumHero()

                DonghuaFeatureCard()

                SectionTitle(
                    title = "Gratis vs Premium",
                    subtitle = "Yang kamu dapat setelah upgrade."
                )
                PremiumComparison()

                SectionTitle(
                    title = "Pilih paket",
                    subtitle = "Ketuk satu paket untuk lanjut ke pembayaran."
                )

                when {
                    uiState.isLoadingPackages -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ZenimePrimary)
                        }
                    }

                    uiState.packagesError != null -> {
                        PremiumErrorCard(
                            message = uiState.packagesError ?: "Gagal memuat daftar paket",
                            onRetry = viewModel::retryLoadPackages
                        )
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.packages.forEach { pkg ->
                                val months = packageMonths(pkg)
                                val perMonth = if (months != null && months > 1) pkg.price / months else null
                                val savePercent = if (perMonth != null && basePerMonth != null && basePerMonth > 0) {
                                    (((basePerMonth - perMonth.toDouble()) / basePerMonth) * 100).toInt()
                                        .takeIf { it >= 5 }
                                } else null

                                PremiumPackageCard(
                                    pkg = pkg,
                                    perMonth = perMonth,
                                    savePercent = savePercent,
                                    selected = uiState.selectedPackage?.id == pkg.id,
                                    onClick = { viewModel.onPackageSelected(pkg) }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Premium aktif di akun ini setelah pembayaran terverifikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PremiumHero() {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(ZenimePrimaryContainer, MaterialTheme.colorScheme.surface)
                )
            )
            .border(1.dp, PremiumGold.copy(alpha = 0.35f), shape)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(PremiumGold.copy(alpha = 0.30f), Color.Transparent)
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_premium_badge),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Zenime Premium",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Donghua tanpa batas, kualitas sampai 1080p, dan nol iklan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DonghuaFeatureCard() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(PremiumGold.copy(alpha = 0.16f), PremiumGold.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, PremiumGold.copy(alpha = 0.45f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PremiumGold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = PremiumGold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                "Semua donghua terbuka",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Nonton donghua episode demi episode tanpa batas. Tanpa Premium, donghua tidak bisa diputar; sinopsis dan daftar episodenya tetap bisa dilihat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** null di kolom Gratis = tidak tersedia (X); null di kolom Premium = tersedia (centang). */
private class CompareRow(val label: String, val free: String?, val premium: String?)

@Composable
private fun PremiumComparison() {
    val rows = listOf(
        CompareRow("Donghua", "Terkunci", "Semua episode"),
        CompareRow("2 episode anime terbaru", "Terkunci", "Langsung buka"),
        CompareRow("Kualitas video", "Maks. 480p", "Sampai 1080p"),
        CompareRow("Iklan", "Ada", "Tanpa iklan"),
        CompareRow("XP nonton", "×1", "×2"),
        CompareRow("Download offline", null, null),
        CompareRow("Voice note di chat", null, null),
        CompareRow("Banner profil custom", null, null),
        CompareRow("Badge Premium", null, null)
    )
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, CardOutlineBorder, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1.3f))
            Text(
                "Gratis",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Premium",
                modifier = Modifier.weight(1.1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PremiumGold
            )
        }

        rows.forEach { row ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CardOutlineBorder)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label,
                    modifier = Modifier.weight(1.3f).padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                CompareCell(text = row.free, positive = false, weight = 1f)
                CompareCell(text = row.premium, positive = true, weight = 1.1f)
            }
        }
    }
}

@Composable
private fun RowScope.CompareCell(text: String?, positive: Boolean, weight: Float) {
    Box(
        modifier = Modifier.weight(weight),
        contentAlignment = Alignment.Center
    ) {
        if (text == null) {
            Icon(
                imageVector = if (positive) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (positive) "Tersedia" else "Tidak tersedia",
                tint = if (positive) PremiumGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (positive) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (positive) PremiumGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "12 bulan" -> 12, "1 tahun" -> 12. null kalau teks durasi gak kebaca angka. */
private fun packageMonths(pkg: PremiumPackage): Int? {
    val n = Regex("\\d+").find(pkg.durationText)?.value?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return if (pkg.durationText.contains("tahun", ignoreCase = true)) n * 12 else n
}

@Composable
private fun PremiumPackageCard(
    pkg: PremiumPackage,
    perMonth: Long?,
    savePercent: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor by animateColorAsState(
        if (selected) ZenimePrimary else CardOutlineBorder,
        label = "packageBorder"
    )
    val fillColor by animateColorAsState(
        if (selected) ZenimePrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        label = "packageFill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillColor)
            .border(if (selected) 1.6.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.6.dp, if (selected) ZenimePrimary else CardOutlineBorder, CircleShape)
                .background(if (selected) ZenimePrimary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pkg.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (pkg.badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PremiumGold.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            pkg.badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PremiumGold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (perMonth != null) "Rp ${formatRupiah(perMonth)} per bulan" else pkg.durationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Rp ${formatRupiah(pkg.price)}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
            if (savePercent != null) {
                Text(
                    "Hemat $savePercent%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = StatusOngoing
                )
            }
        }
    }
}

@Composable
private fun PremiumErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Coba Lagi")
            }
        }
    }
}

private fun formatRupiah(amount: Long): String {
    val s = amount.toString()
    val sb = StringBuilder()
    for ((index, char) in s.reversed().withIndex()) {
        if (index != 0 && index % 3 == 0) sb.append('.')
        sb.append(char)
    }
    return sb.reverse().toString()
}

/** Buka halaman storefront di browser dengan kode akun + id paket sebagai query. */
private fun openCheckout(context: android.content.Context, baseUrl: String, code: String, packageId: String) {
    val uri = Uri.parse(baseUrl)
        .buildUpon()
        .appendQueryParameter("code", code)
        .appendQueryParameter("package_id", packageId)
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
