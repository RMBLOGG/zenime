package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Badge "gamer rank" yang dipakai bareng-bareng di Chat Global, Leaderboard
 * XP, dan daftar Clan -- biar tampilannya KONSISTEN di semua tempat.
 * Jangan bikin versi badge sendiri-sendiri lagi di screen lain, import dari
 * sini aja.
 */

/**
 * Bentuk badge panah/pita (chevron ribbon) — sisi kiri ada takik masuk,
 * sisi kanan runcing kayak anak panah. Dipakai buat badge level.
 */
val BadgeArrowShape = GenericShape { size, _ ->
    val tip = size.height * 0.42f
    moveTo(size.height * 0.28f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(size.height * 0.28f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** Badge level emas dengan ikon zcoin, bentuk panah runcing sebelah. */
@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(BadgeArrowShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFFDE7A), Color(0xFFE8A317), Color(0xFFB8860B))
                )
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_zcoin_badge),
            contentDescription = null,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "Lv.$level",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp
            )
        )
    }
}

/**
 * Bentuk badge clan — heksagon pita runcing di dua sisi (kiri & kanan),
 * lebih "gamer rank" dibanding badge level yang cuma runcing sebelah.
 */
val BadgeHexShape = GenericShape { size, _ ->
    val tip = size.height * 0.5f
    moveTo(tip, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(tip, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** Badge tag clan rainbow animasi (fill geser + kilau + border berdenyut). */
@Composable
fun ClanRainbowBadge(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "clanRainbow")
    // Warna gradient rainbow yang geser terus-terusan (efek holografik)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clanRainbowPhase"
    )
    // Border luar yang berdenyut (glow pulse) biar makin "hidup"
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clanGlowPulse"
    )
    // Kilau diagonal yang lewat dari kiri ke kanan tiap beberapa detik
    val shinePhase by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clanShine"
    )
    val rainbowColors = listOf(
        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00),
        Color(0xFF34C759), Color(0xFF00C7BE), Color(0xFF30ADE6),
        Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF3B30)
    )
    val sweep = 260f
    val startX = -sweep + phase * (sweep * 2f)
    val fillBrush = Brush.linearGradient(
        colors = rainbowColors,
        start = Offset(startX, 0f),
        end = Offset(startX + sweep, 30f)
    )
    val shineBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0f)
        ),
        start = Offset(shinePhase * 200f - 60f, 0f),
        end = Offset(shinePhase * 200f + 60f, 26f)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(BadgeHexShape)
            .background(fillBrush)
            .background(shineBrush)
            .border(width = 1.dp, color = Color.White.copy(alpha = glowAlpha), shape = BadgeHexShape)
            .padding(horizontal = 10.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = "✦",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            )
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "✦",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp)
        )
    }
}
