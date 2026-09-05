package com.example.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import com.example.data.local.PremiumStatusCache
import com.example.data.repository.AnimeRepository
import com.example.data.repository.PremiumRepository
import com.example.ui.theme.ZenimePrimary

private sealed interface GateState {
    data object Checking : GateState
    data class Resolved(val isPremium: Boolean) : GateState
}

/**
 * Resolve status premium user SEBELUM render player, buat dipassing ke
 * [content] (dipakai konsumen buat nentuin tampilin iklan/cap kualitas/lock
 * episode/dll -- lihat util.PremiumAccess). Gate ini SENDIRI bukan pintu
 * terkunci -- cuma nunggu tau isPremium-nya apa dulu sebelum lanjut. Blokir
 * beneran (episode di luar trial gratis, lihat isEpisodeLocked) dihandle di
 * consumer-nya (PlayerScreen), bukan di sini.
 *
 * Kalau cek live ke server gagal (misal lagi offline -- kasus umum pas
 * mau nonton episode yang udah didownload), fallback ke
 * [PremiumStatusCache]: dipakai HANYA kalau cache masih dalam TTL-nya dan
 * expiresAt user belum lewat (lihat PremiumStatusCache buat detail).
 * Kalau gak ada sinyal sama sekali (gak pernah cek sukses sebelumnya
 * ATAU belum login), default ke non-premium -- BUKAN diblokir, cuma gak
 * dapet benefit premium (kena iklan, kualitas dibatasin), video-nya
 * sendiri tetap bisa diputer.
 */
@Composable
fun PremiumGate(
    firebaseUid: String?,
    episodeId: String,
    animeId: String,
    repository: AnimeRepository,
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    content: @Composable (isPremium: Boolean) -> Unit
) {
    var state by remember(firebaseUid, episodeId) { mutableStateOf<GateState>(GateState.Checking) }
    val context = LocalContext.current
    val statusCache = remember(context) { PremiumStatusCache(context) }

    LaunchedEffect(firebaseUid, episodeId) {
        state = GateState.Checking

        val premiumResult = if (firebaseUid.isNullOrBlank()) {
            null
        } else {
            PremiumRepository(statusCache).checkPremiumStatus(firebaseUid)
        }

        val isPremium = if (premiumResult != null && premiumResult.isSuccess) {
            premiumResult.getOrNull()?.isPremium ?: false
        } else {
            // Cek live gagal -- fallback ke cache (ngurus sendiri TTL &
            // expiresAt-nya, lihat PremiumStatusCache), kalau gak ada
            // default ke false. Baik cache maupun default di sini CUMA
            // ngaruh ke benefit (iklan/kualitas), BUKAN akses nonton.
            statusCache.getValidOfflineStatus() ?: false
        }

        state = GateState.Resolved(isPremium)
    }

    when (val s = state) {
        is GateState.Checking -> {
            Scaffold(containerColor = Color.Black) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ZenimePrimary)
                }
            }
        }

        is GateState.Resolved -> content(s.isPremium)
    }
}
