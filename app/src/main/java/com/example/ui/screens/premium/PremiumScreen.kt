package com.example.ui.screens.premium

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.SupabaseConfig
import com.example.data.model.PremiumPackage
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                Text(
                    "Pilih Paket Premium",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Nonton tanpa batas dengan akun Premium. Pilih paket, salin kode akun, lalu selesaikan pembayaran lewat Zenime Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                PremiumPackageCard(
                                    pkg = pkg,
                                    selected = uiState.selectedPackage?.id == pkg.id,
                                    onClick = { viewModel.onPackageSelected(pkg) }
                                )
                            }
                        }
                    }
                }

                if (uiState.selectedPackage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PremiumCheckoutCard(
                        pkg = uiState.selectedPackage!!,
                        isLoadingCode = uiState.isLoadingCode,
                        zenimeCode = uiState.zenimeCode,
                        codeError = uiState.codeError,
                        onRetryCode = viewModel::retryLoadCode
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumPackageCard(
    pkg: PremiumPackage,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.6.dp else 1.dp,
                color = if (selected) ZenimePrimary else CardOutlineBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (pkg.badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ZenimePrimary.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        pkg.badge.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZenimePrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        pkg.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Rp ${formatRupiah(pkg.price)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = ZenimePrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "/ ${pkg.durationText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.6.dp,
                            color = if (selected) ZenimePrimary else CardOutlineBorder,
                            shape = CircleShape
                        )
                        .background(if (selected) ZenimePrimary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCheckoutCard(
    pkg: PremiumPackage,
    isLoadingCode: Boolean,
    zenimeCode: String?,
    codeError: String?,
    onRetryCode: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ZenimePrimary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = ZenimePrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Paket ${pkg.label} · Rp ${formatRupiah(pkg.price)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoadingCode -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ZenimePrimary, modifier = Modifier.size(28.dp))
                    }
                }

                codeError != null -> {
                    PremiumErrorCard(message = codeError, onRetry = onRetryCode)
                }

                zenimeCode != null -> {
                    Text(
                        "Kode Akun Zenime Kamu",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            zenimeCode,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ZenimePrimary
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(zenimeCode))
                            }
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Salin kode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Salin kode di atas, lalu tempel kode-nya di halaman pembayaran Zenime Store buat aktifin Premium ke akun ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupabaseConfig.STOREFRONT_URL))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bayar Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
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
