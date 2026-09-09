package com.example.ui.screens.coin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.api.SupabaseConfig
import com.example.data.model.CoinPackage
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimePrimary

@Composable
fun CoinScreen(
    viewModel: CoinViewModel,
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
                title = { ZenimeScreenTitle(title = "ZCoin") }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoinBalanceCard(
                    isLoading = uiState.isLoadingBalance,
                    balance = uiState.balance,
                    error = uiState.balanceError,
                    onRefresh = viewModel::loadBalance
                )

                Text(
                    "Pilih Paket Top Up",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Salin kode akun setelah pilih paket, lalu selesaikan pembayaran lewat Zenime Store.",
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
                        CoinErrorCard(
                            message = uiState.packagesError ?: "Gagal memuat daftar paket",
                            onRetry = viewModel::retryLoadPackages
                        )
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.packages.forEach { pkg ->
                                CoinPackageCard(
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
                    CoinCheckoutCard(
                        pkg = uiState.selectedPackage!!,
                        isLoadingCode = uiState.isLoadingCode,
                        zenimeCode = uiState.zenimeCode,
                        codeError = uiState.codeError,
                        onRetryCode = viewModel::retryLoadCode,
                        onReturnedFromCheckout = viewModel::loadBalance
                    )
                }
            }
        }
    }
}

@Composable
private fun CoinBalanceCard(
    isLoading: Boolean,
    balance: Long,
    error: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.6.dp, ZenimePrimary, RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ZenimePrimary.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zcoin_badge),
                        contentDescription = "ZCoin",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "Saldo ZCoin Kamu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when {
                            isLoading -> CircularProgressIndicator(
                                color = ZenimePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            error != null -> Text(
                                "Gagal memuat",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Text(
                                "${formatRupiah(balance)} ZCoin",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = ZenimePrimary
                            )
                        }
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh saldo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CoinPackageCard(
    pkg: CoinPackage,
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${formatRupiah(pkg.totalCoin)} ZCoin",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (pkg.bonusCoin > 0) {
                    Text(
                        "${formatRupiah(pkg.coinAmount)} + bonus ${formatRupiah(pkg.bonusCoin)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenimePrimary
                    )
                }
                Text(
                    "Rp ${formatRupiah(pkg.price)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun CoinCheckoutCard(
    pkg: CoinPackage,
    isLoadingCode: Boolean,
    zenimeCode: String?,
    codeError: String?,
    onRetryCode: () -> Unit,
    onReturnedFromCheckout: () -> Unit
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
                Image(
                    painter = painterResource(id = R.drawable.ic_zcoin_badge),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${formatRupiah(pkg.totalCoin)} ZCoin · Rp ${formatRupiah(pkg.price)}",
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
                    CoinErrorCard(message = codeError, onRetry = onRetryCode)
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
                            onClick = { clipboardManager.setText(AnnotatedString(zenimeCode)) }
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
                        "Salin kode di atas, lalu tempel kode-nya di halaman top up Zenime Store buat nambahin ZCoin ke akun ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val storefrontUri = Uri.parse(SupabaseConfig.COIN_STOREFRONT_URL)
                                .buildUpon()
                                .appendQueryParameter("code", zenimeCode)
                                .appendQueryParameter("package_id", pkg.id)
                                .build()
                            val intent = Intent(Intent.ACTION_VIEW, storefrontUri)
                            context.startActivity(intent)
                            // Balik dari browser saldo mungkin belum ke-update kalau
                            // pembayaran instan -- refresh biar user gak perlu keluar-masuk layar.
                            onReturnedFromCheckout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bayar Sekarang", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val manualUri = Uri.parse(SupabaseConfig.COIN_MANUAL_STOREFRONT_URL)
                                .buildUpon()
                                .appendQueryParameter("code", zenimeCode)
                                .appendQueryParameter("package_id", pkg.id)
                                .build()
                            val intent = Intent(Intent.ACTION_VIEW, manualUri)
                            context.startActivity(intent)
                            onReturnedFromCheckout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Bayar dari Luar Negeri", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinErrorCard(message: String, onRetry: () -> Unit) {
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
