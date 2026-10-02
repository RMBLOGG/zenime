package com.example.ui.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.screens.splash.SplashScreen
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icon: ImageVector,
    val title: String,
    val desc: String
)

private val slides = listOf(
    OnboardingSlide(
        icon = Icons.Filled.PlayCircle,
        title = "Nonton Anime Sepuasnya",
        desc = "Ribuan judul favoritmu bisa langsung ditonton dari HP Android."
    ),
    OnboardingSlide(
        icon = Icons.Filled.Download,
        title = "Download & Nonton Offline",
        desc = "Simpan episode favorit, terus tonton kapan aja tanpa kuota."
    ),
    OnboardingSlide(
        icon = Icons.Filled.Groups,
        title = "Ngobrol & Gabung Clan",
        desc = "Chat Global, bikin Clan, dan tambah teman sesama penonton."
    ),
    OnboardingSlide(
        icon = Icons.Filled.EmojiEvents,
        title = "Naik Level, Kumpulin XP",
        desc = "Makin sering nonton, makin tinggi levelmu di Leaderboard."
    )
)

/**
 * Alur intro: slide onboarding -> SplashScreen yang udah ada -> [onFinished].
 * Panggil [onFinished] di sini buat nyimpen flag "sudah lihat intro".
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var showSplash by rememberSaveable { mutableStateOf(false) }
    if (showSplash) {
        SplashScreen(onSplashFinished = onFinished)
    } else {
        OnboardingPager(onDone = { showSplash = true })
    }
}

@Composable
private fun OnboardingPager(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == slides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Tombol Lewati (hilang di slide terakhir, tinggi tetap biar layout gak loncat)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLast) {
                TextButton(onClick = onDone) {
                    Text("Lewati", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = slide.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = slide.desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Indikator titik
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(slides.size) { index ->
                val selected = pagerState.currentPage == index
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Button(
            onClick = {
                if (isLast) onDone()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp)
        ) {
            Text(
                text = if (isLast) "Mulai" else "Lanjut",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
