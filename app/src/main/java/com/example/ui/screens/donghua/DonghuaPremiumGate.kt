package com.example.ui.screens.donghua

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.PremiumStatusCache
import com.example.data.repository.PremiumRepository
import com.example.ui.components.PremiumLockedScreen
import com.example.ui.theme.ZenimePrimary

private sealed interface DonghuaGateState {
    data object Checking : DonghuaGateState
    data class Resolved(val isPremium: Boolean) : DonghuaGateState
}

/**
 * Gate nonton donghua: BENERAN ngeblok pemutar kalau non-premium.
 * Daftar & detail donghua (sinopsis, daftar episode) tetap kebuka buat
 * semua orang -- lock cuma pas masuk player.
 *
 * Belum login / cek live gagal -> fallback ke cache offline
 * ([PremiumStatusCache], sudah ngurus TTL & expiresAt sendiri), kalau
 * gak ada juga = non-premium.
 */
@Composable
fun DonghuaPremiumGate(
    firebaseUid: String?,
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val statusCache = remember(context) { PremiumStatusCache(context) }
    var state by remember(firebaseUid) { mutableStateOf<DonghuaGateState>(DonghuaGateState.Checking) }

    LaunchedEffect(firebaseUid) {
        state = DonghuaGateState.Checking
        val res = if (firebaseUid.isNullOrBlank()) null
        else PremiumRepository(statusCache).checkPremiumStatus(firebaseUid)
        val isPremium = if (res != null && res.isSuccess) {
            res.getOrNull()?.isPremium ?: false
        } else {
            statusCache.getValidOfflineStatus() ?: false
        }
        state = DonghuaGateState.Resolved(isPremium)
    }

    when (val s = state) {
        is DonghuaGateState.Checking -> Scaffold(containerColor = Color.Black) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = ZenimePrimary) }
        }
        is DonghuaGateState.Resolved ->
            if (s.isPremium) content()
            else DonghuaLockedScreen(onBackClick = onBackClick, onUpgradeClick = onUpgradeClick)
    }
}

@Composable
private fun DonghuaLockedScreen(onBackClick: () -> Unit, onUpgradeClick: () -> Unit) {
    PremiumLockedScreen(
        headline = "Donghua khusus member Premium",
        description = "Sinopsis dan daftar episode tetap bisa dibuka tanpa Premium.",
        benefits = listOf("Nonton semua episode donghua", "Bebas iklan", "Kualitas HD"),
        onUpgradeClick = onUpgradeClick,
        onBackClick = onBackClick,
        accent = ZenimePrimary
    )
}
