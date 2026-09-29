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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.api.SupabaseConfig
import com.example.data.model.CoinPackage
import com.example.ui.components.CheckoutSheet
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

    val context = LocalContext.current
    uiState.selectedPackage?.let { pkg ->
        CheckoutSheet(
            title = "${formatRupiah(pkg.totalCoin)} ZCoin",
            priceText = "Rp ${formatRupiah(pkg.price)}",
            isLoadingCode = uiState.isLoadingCode,
            zenimeCode = uiState.zenimeCode,
            codeError = uiState.codeError,
            onRetryCode = viewModel::retryLoadCode,
            onPayQris = {
                uiState.zenimeCode?.let { code ->
                    openCheckout(context, SupabaseConfig.COIN_STOREFRONT_URL, code, pkg.id)
                    viewModel.loadBalance()
                }
            },
            onPayManual = {
                // Buat pembeli yang QRIS otomatisnya gak kebaca e-wallet/bank
                // mereka (mis. luar negeri) -- verifikasi dilakukan manual admin.
                uiState.zenimeCode?.let { code ->
                    openCheckout(context, SupabaseConfig.COIN_MANUAL_STOREFRONT_URL, code, pkg.id)
                    viewModel.loadBalance()
                }
            },
            onDismiss = viewModel::clearSelection
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_zcoin_badge),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(CircleShape)
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
                    "Ketuk satu paket untuk lanjut ke pembayaran.",
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

/** Buka halaman storefront di browser dengan kode akun + id paket sebagai query. */
private fun openCheckout(context: android.content.Context, baseUrl: String, code: String, packageId: String) {
    val uri = Uri.parse(baseUrl)
        .buildUpon()
        .appendQueryParameter("code", code)
        .appendQueryParameter("package_id", packageId)
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
