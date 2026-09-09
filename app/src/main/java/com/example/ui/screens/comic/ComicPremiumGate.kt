package com.example.ui.screens.comic

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.repository.PremiumRepository
import com.example.ui.theme.ZenimePrimary

private sealed interface ComicGateState {
    data object Checking : ComicGateState
    data class Resolved(val isPremium: Boolean) : ComicGateState
}

/**
 * Gate khusus buat baca chapter komik (ComicReaderScreen). Beda sama
 * [com.example.ui.screens.player.PremiumGate] yang cuma nunggu status
 * buat nentuin benefit -- gate ini BENERAN ngeblok konten kalau
 * non-premium, karena baca komik sendiri (bukan cuma benefit kayak
 * kualitas/iklan) yang jadi fitur premium.
 *
 * List & detail komik TETAP kebuka buat semua orang -- lock cuma pas
 * mau masuk reader. Kalau cek live gagal ATAU belum login, default ke
 * non-premium (gak ada fallback cache offline kayak di player, karena
 * baca komik butuh koneksi buat load gambar chapter juga).
 */
@Composable
fun ComicPremiumGate(
    firebaseUid: String?,
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var state by remember(firebaseUid) { mutableStateOf<ComicGateState>(ComicGateState.Checking) }

    LaunchedEffect(firebaseUid) {
        state = ComicGateState.Checking
        val isPremium = if (firebaseUid.isNullOrBlank()) {
            false
        } else {
            PremiumRepository().checkPremiumStatus(firebaseUid).getOrNull()?.isPremium ?: false
        }
        state = ComicGateState.Resolved(isPremium)
    }

    when (val s = state) {
        is ComicGateState.Checking -> {
            Scaffold(containerColor = Color.Black) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ZenimePrimary)
                }
            }
        }

        is ComicGateState.Resolved -> {
            if (s.isPremium) {
                content()
            } else {
                ComicLockedScreen(onBackClick = onBackClick, onUpgradeClick = onUpgradeClick)
            }
        }
    }
}

@Composable
private fun ComicLockedScreen(
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = ZenimePrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Khusus Member Premium",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Baca chapter komik ini butuh langganan Premium Zenime. Kamu masih bisa lihat sinopsis & daftar chapter tanpa Premium.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(
                onClick = onUpgradeClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ZenimePrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upgrade ke Premium")
            }
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Kembali", color = Color.White)
            }
        }
    }
}
