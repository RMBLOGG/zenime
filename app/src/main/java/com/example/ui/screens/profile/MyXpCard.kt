package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark
import com.example.util.XpLevelFormula

/**
 * Kartu ringkas "Level nonton" buat ditaruh di ProfileScreen -- progress bar
 * XP ke level berikutnya + tombol ke leaderboard. Muat data-nya sendiri
 * (lihat [MyXpViewModel]), jadi cukup tempel `MyXpCard(firebaseUid = uid, ...)`
 * tanpa perlu ubah state ProfileViewModel yang udah ada.
 */
@Composable
fun MyXpCard(firebaseUid: String, onLeaderboardClick: () -> Unit) {
    val viewModel: MyXpViewModel = viewModel(
        key = "my_xp_$firebaseUid",
        factory = viewModelFactory { initializer { MyXpViewModel(firebaseUid) } }
    )
    val uiState by viewModel.uiState.collectAsState()

    // Belum pernah nonton sama sekali = belum ada baris di user_xp. Anggap
    // level 1 / 0 XP daripada nyembunyiin kartunya -- biar user baru langsung
    // liat ada fitur ini dan kepancing buat mulai nonton.
    val level = uiState.userXp?.level ?: 1
    val totalXp = uiState.userXp?.totalXp ?: 0L
    val progressInfo = XpLevelFormula.progress(totalXp, level)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onLeaderboardClick),
        colors = CardDefaults.cardColors(containerColor = ZenimeSurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ZenimePrimary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.height(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Level $level", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "dari nonton anime",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Leaderboard \u203A",
                    color = ZenimePrimary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progressInfo.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = ZenimePrimary,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${progressInfo.xpIntoLevel} / ${progressInfo.xpNeededForLevel} XP ke Level ${level + 1}",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
