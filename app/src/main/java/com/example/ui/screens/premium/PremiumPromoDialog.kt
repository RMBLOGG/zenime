package com.example.ui.screens.premium

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.PremiumPackage
import com.example.ui.theme.ZenimePrimary

/**
 * Promo full-screen buat upsell Premium -- ditampilin OTOMATIS sekali tiap
 * app baru dibuka (lihat pemanggilnya di NavGraph: cuma trigger sekali per
 * sesi app & cuma buat user yang UDAH LOGIN dan BUKAN premium). Beda sama
 * [PremiumScreen] yang isinya alur checkout beneran (kode akun, dll) --
 * dialog ini murni ajakan, CTA-nya lempar ke [PremiumScreen] buat lanjutin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumPromoDialog(
    isLoading: Boolean,
    packages: List<PremiumPackage>,
    onDismiss: () -> Unit,
    onSubscribeClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 110.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp)
                    ) {
                        Text(
                            "Zenime Premium",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Bebas iklan, download offline, dan lebih banyak lagi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ZenimePrimary)
                        }
                    }
                    packages.isEmpty() -> {
                        // Gak ada paket buat ditampilin -- jangan paksain promo
                        // kosong, biarin user lanjut ke app seperti biasa.
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Paket premium belum tersedia saat ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        val pagerState = rememberPagerState(
                            initialPage = packages.indexOfFirst { it.badge != null }.coerceAtLeast(0),
                            pageCount = { packages.size }
                        )

                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            pageSpacing = 14.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            PremiumPromoPackageCard(pkg = packages[page])
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            packages.indices.forEach { index ->
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
            }

            // CTA nempel di bawah (bukan ikut scroll), biar selalu keliatan
            // gimana pun panjangnya konten di atasnya.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Button(
                    onClick = onSubscribeClick,
                    enabled = packages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        "Mulai Berlangganan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Nanti dulu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                )
            }
        }
        }
    }
}

/** Satu kartu paket di [HorizontalPager] promo -- gaya kartu "Mega Fan" Crunchyroll. */
@Composable
private fun PremiumPromoPackageCard(pkg: PremiumPackage) {
    val benefits = listOf(
        "Custom foto profile",
        "Download anime buat ditonton offline",
        "Bebas iklan sepenuhnya",
        "Resolusi unlock, dari terendah sampai 1080p",
        "Badge khusus Premium"
    )

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.6.dp, ZenimePrimary, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pkg.badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, ZenimePrimary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    pkg.badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ZenimePrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_premium_badge),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            pkg.label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = ZenimePrimary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Rp ${formatRupiahPromo(pkg.price)}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "/ ${pkg.durationText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            benefits.forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ZenimePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    }
}

private fun formatRupiahPromo(amount: Long): String {
    val s = amount.toString()
    val sb = StringBuilder()
    for ((index, char) in s.reversed().withIndex()) {
        if (index != 0 && index % 3 == 0) sb.append('.')
        sb.append(char)
    }
    return sb.reverse().toString()
}
