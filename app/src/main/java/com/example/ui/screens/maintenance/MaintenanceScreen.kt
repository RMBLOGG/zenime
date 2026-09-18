package com.example.ui.screens.maintenance

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeOnSurfaceVariantDark
import kotlinx.coroutines.delay

/**
 * Layar SATU-SATUNYA yang ditampilkan kalau "maintenance_mode" aktif di
 * Firebase Remote Config (lihat RemoteConfigManager.isMaintenanceMode()).
 * Sama seperti ForceUpdateScreen, sengaja tidak ada tombol skip/back --
 * MainActivity gak nge-compose ZenimeAppNavHost sama sekali selama flag ini
 * true.
 *
 * Desain sengaja dibikin beda dari ForceUpdateScreen (lebih "hidup", nuansa
 * ruang kontrol/monitoring server) tapi tetap satu bahasa visual dengan
 * identitas Zenime: aksen crimson [MaterialTheme.colorScheme.primary] di
 * atas navy gelap, tombol pill penuh, tipografi yang sama.
 *
 * Auto-retry: hitung mundur [autoRetrySeconds] lalu panggil [onRetry]
 * sendiri (biar user gak perlu manual pantengin), tapi tombol "Coba
 * Sekarang" tetap ada buat yang gak mau nunggu.
 */
@Composable
fun MaintenanceScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    autoRetrySeconds: Int = 20
) {
    val accent = MaterialTheme.colorScheme.primary

    // --- Auto-retry countdown, restart otomatis tiap siklus ---
    var secondsLeft by remember { mutableIntStateOf(autoRetrySeconds) }
    LaunchedEffect(Unit) {
        while (true) {
            secondsLeft = autoRetrySeconds
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            onRetry()
        }
    }

    // --- Animasi ---
    val transition = rememberInfiniteTransition(label = "maintenance_fx")

    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "ringRotation"
    )
    val orbitRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "orbitRotation"
    )
    val corePulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val statusDotAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusDotAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // --- Node visual: ring berputar + satelit orbit + inti kaca berdenyut ---
        Box(
            modifier = Modifier.size(188.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(188.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent)
                        )
                    )
            )

            Canvas(
                modifier = Modifier
                    .size(168.dp)
                    .rotate(ringRotation)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            accent.copy(alpha = 0f),
                            accent.copy(alpha = 0.9f),
                            accent.copy(alpha = 0f)
                        )
                    ),
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(168.dp)
                    .rotate(orbitRotation),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            Brush.radialGradient(listOf(accent, accent.copy(alpha = 0f))),
                            CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size((104 * corePulse).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, CardOutlineBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // --- Status pill berkedip ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent.copy(alpha = statusDotAlpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SERVER OFFLINE",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = ZenimeOnSurfaceVariantDark,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Memindai ulang koneksi server…",
            style = MaterialTheme.typography.labelMedium,
            color = ZenimeOnSurfaceVariantDark,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = accent,
            trackColor = CardOutlineBorder
        )

        Spacer(modifier = Modifier.height(22.dp))

        RetryButton(
            text = "Coba Sekarang",
            accent = accent,
            onClick = {
                secondsLeft = autoRetrySeconds
                onRetry()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Otomatis dicoba ulang dalam ${secondsLeft}s",
            style = MaterialTheme.typography.bodySmall,
            color = ZenimeOnSurfaceVariantDark
        )

        Spacer(modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun RetryButton(text: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(999.dp),
        color = accent
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
