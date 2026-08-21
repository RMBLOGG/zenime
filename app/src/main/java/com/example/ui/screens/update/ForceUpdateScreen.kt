package com.example.ui.screens.update

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.DownloadState

/**
 * Layar SATU-SATUNYA yang ditampilkan kalau versionCode APK yang jalan
 * < "min_version_code" dari Firebase Remote Config. Sengaja TIDAK ada
 * tombol back/skip -- ini bukan dialog di atas app, tapi pengganti total
 * konten app (lihat pemanggilannya di MainActivity: kalau needsUpdate
 * true, ZenimeAppNavHost sama sekali gak di-compose).
 *
 * UPDATE-NYA DOWNLOAD LANGSUNG DI DALAM APP (bukan redirect ke browser
 * lagi) -- [downloadState] datang dari ApkDownloader di MainActivity dan
 * nentuin tombol mana yang tampil:
 * - Idle       -> "Update Sekarang" (mulai download, [onDownloadClick])
 * - Downloading -> progress bar + persen
 * - Downloaded -> "Install Sekarang" (buka installer sistem, [onInstallClick])
 * - Failed     -> pesan error + "Coba Lagi" ([onRetryClick])
 */
@Composable
fun ForceUpdateScreen(
    message: String,
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zenime_logo_1786121211149),
                    contentDescription = "Zenime Logo",
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Update Tersedia",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                // Pas gagal, ganti pesannya jadi alasan gagalnya -- lebih
                // kepake buat user daripada tetep nampilin pesan update umum.
                text = when (downloadState) {
                    is DownloadState.Failed -> downloadState.message
                    else -> message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Update Sekarang",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                is DownloadState.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Mengunduh… ${downloadState.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is DownloadState.Downloaded -> {
                    Button(
                        onClick = onInstallClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Install Sekarang",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                is DownloadState.Failed -> {
                    Button(
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Coba Lagi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
