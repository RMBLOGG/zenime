package com.example.ui.screens.donation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary

/**
 * Halaman donasi penuh -- konsepnya niru "Donators Hall of Fame" ala
 * Sankanime (satu halaman berisi beberapa metode pembayaran), tapi versi
 * native & lebih ringkas: tiap metode jadi kartu, di-tap langsung buka
 * dialog QRIS atau browser ke link donasi terkait.
 *
 * Ganti nilai di [DonationConfig] kalau username/link donasinya beda.
 */
@Composable
fun DonationScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showQrisDialog by remember { mutableStateOf(false) }

    if (showQrisDialog) {
        QrisDonationDialog(onDismiss = { showQrisDialog = false })
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada browser untuk membuka link ini", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
        topBar = {
            ZenimeHeader(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        ZenimeScreenTitle(title = "Dukung Kami")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZenimeBackgroundDark)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Intro, mirip header "Donators Hall of Fame" di Sankanime
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    "Dukung ${DonationConfig.APP_NAME}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Donasimu bantu jaga server tetap nyala, cepat, dan konten tetap update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DonationMethodCard(
                icon = Icons.Filled.QrCode2,
                iconTint = ZenimePrimary,
                tag = "Lokal \u00b7 Indonesia",
                title = "QRIS",
                subtitle = "GoPay, OVO, DANA, ShopeePay, m-Banking",
                buttonLabel = "Tampilkan QRIS",
                onClick = { showQrisDialog = true }
            )

            DonationMethodCard(
                icon = Icons.Filled.Favorite,
                iconTint = Color(0xFF00B2FF),
                tag = "Global",
                title = "SociaBuzz",
                subtitle = "Kartu kredit, PayPal, QRIS/e-wallet",
                buttonLabel = "Buka SociaBuzz",
                onClick = { openUrl(DonationConfig.SOCIABUZZ_URL) }
            )

            DonationMethodCard(
                icon = Icons.Filled.Favorite,
                iconTint = Color(0xFFFF4949),
                tag = "Lokal \u00b7 Indonesia",
                title = "Trakteer",
                subtitle = "QRIS, GoPay/OVO, DANA/ShopeePay, Transfer Bank",
                buttonLabel = "Buka Trakteer",
                onClick = { openUrl(DonationConfig.TRAKTEER_URL) }
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Setiap dukungan, sekecil apa pun, sangat berarti. Terima kasih! \u2764\ufe0f",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Objek konfigurasi sentral biar gampang diganti kalau username/link-nya beda. */
object DonationConfig {
    const val APP_NAME = "Zenime"
    const val SOCIABUZZ_URL = "https://sociabuzz.com/Dayynime/tribe"
    const val TRAKTEER_URL = "https://trakteer.id/Dayynimee"
}

@Composable
private fun DonationMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    tag: String,
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardOutlineBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    buttonLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = iconTint
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Popup QRIS -- dipindah dari [com.example.ui.screens.home.DonationSection] biar bisa dipakai di sini juga. */
@Composable
private fun QrisDonationDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "Dukung ${DonationConfig.APP_NAME}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Image(
                    painter = painterResource(id = R.drawable.qris_donasi),
                    contentDescription = "QRIS donasi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                )

                Text(
                    "Scan pakai aplikasi e-wallet atau m-banking apa saja",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Tutup")
                }
            }
        }
    }
}
