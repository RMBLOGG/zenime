package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.common.Result
import com.example.data.repository.AnimeRepository
import com.example.data.repository.PremiumRepository
import com.example.ui.theme.ZenimePrimary
import com.example.util.isEpisodeLocked

private sealed interface GateState {
    data object Checking : GateState
    data class Allowed(val isPremium: Boolean) : GateState
    data object Blocked : GateState
    data class CheckFailed(val message: String) : GateState
}

/**
 * Bungkus konten player di balik pengecekan akses. Episode 1-3 gratis buat
 * siapa aja (lihat util.isEpisodeLocked), episode 4 ke atas -- termasuk
 * yang paling baru -- ekslusif Premium. Selama proses cek, tampilin
 * loading. Kalau gagal cek (misal gak ada internet), tetap dianggap
 * Blocked -- jangan biarin nonton kalau statusnya gak bisa dipastikan.
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

    LaunchedEffect(firebaseUid, episodeId) {
        state = GateState.Checking

        val isPremium = if (firebaseUid.isNullOrBlank()) {
            false
        } else {
            PremiumRepository().checkPremiumStatus(firebaseUid).getOrNull()?.isPremium ?: false
        }

        if (isPremium) {
            state = GateState.Allowed(true)
            return@LaunchedEffect
        }

        // Non-premium (atau belum login) -- cek dulu apakah episode ini
        // termasuk yang dikunci (episode 1-3) sebelum mutusin Allowed/Blocked.
        // AnimeRepository nge-cache daftar episode, jadi ini murah kalau
        // DetailScreen/PlayerViewModel udah pernah nge-load duluan.
        repository.getAllEpisodes(animeId).collect { result ->
            when (result) {
                is Result.Success -> {
                    val episode = result.data.find { it.id == episodeId }
                    state = if (isEpisodeLocked(episode?.index, isPremium = false)) {
                        GateState.Blocked
                    } else {
                        GateState.Allowed(false)
                    }
                }
                is Result.Error -> {
                    state = GateState.CheckFailed(result.message)
                }
                Result.Loading -> {}
            }
        }
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

        is GateState.Allowed -> content(s.isPremium)

        is GateState.Blocked -> {
            PremiumRequiredScreen(
                message = "Episode ini khusus buat member Premium. Aktifkan Premium dulu buat lanjut nonton.",
                onBackClick = onBackClick,
                onUpgradeClick = onUpgradeClick
            )
        }

        is GateState.CheckFailed -> {
            PremiumRequiredScreen(
                message = "Gagal memeriksa status akses kamu (${s.message}). Coba lagi sebentar.",
                onBackClick = onBackClick,
                onUpgradeClick = onUpgradeClick
            )
        }
    }
}

@Composable
private fun PremiumRequiredScreen(
    message: String,
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ZenimePrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = ZenimePrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Konten Premium",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lihat Paket Premium", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Kembali")
            }
        }
    }
}
