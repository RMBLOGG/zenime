package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Palet warna avatar -- dipilih yang senada sama tema merah/oranye Zenime, plus beberapa variasi biar gak monoton kalau banyak user. */
private val avatarPalette = listOf(
    Color(0xFFE53E5A), // merah Zenime
    Color(0xFFEF6C3A), // oranye
    Color(0xFFF2A93B), // kuning keemasan
    Color(0xFF7C5CE0), // ungu
    Color(0xFF3D8BFF), // biru
    Color(0xFF2FB380), // hijau
    Color(0xFFE0507A), // pink
    Color(0xFF4FB8C4)  // teal
)

/**
 * Avatar yang di-generate otomatis (bukan foto asli) -- background warna
 * solid + inisial nama, deterministik berdasarkan `seed` (biasanya
 * firebase_uid) biar warnanya konsisten tiap kali dirender ulang buat
 * user yang sama, tanpa perlu simpen apa-apa ke server.
 */
@Composable
fun GeneratedAvatar(
    seed: String,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
) {
    val color = avatarPalette[abs(seed.hashCode()) % avatarPalette.size]
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (borderColor != null) {
                    Modifier.border(borderWidth, borderColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.2).sp,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
