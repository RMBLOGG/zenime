package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ZenimeRole

/**
 * Badge role gaya "cyber cut": sudut dipotong (chamfer) beda tiap role,
 * fill gradient neon + outline gelap tipis, ikon + label.
 * Outline gelap misahin badge dari banner profil yang warnanya mirip.
 * Hierarki efek: Developer = kilau geser terus, Admin = kalem, Moderator = statis.
 *
 * Role + warna diambil dari [RoleBadgeCache] (batch, sama kayak centang),
 * jadi aman dipasang di list. Kalau user gak punya role, gak render apa-apa.
 */
@Composable
fun RoleBadge(
    firebaseUid: String?,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    height: Dp? = null
) {
    val uid = firebaseUid.orEmpty()
    LaunchedEffect(uid) { RoleBadgeCache.request(uid) }
    val roles by RoleBadgeCache.roles.collectAsState()
    val info = roles[uid] ?: return
    RoleBadgeChip(info = info, modifier = modifier, animated = animated, height = height)
}

private fun roleBadgeShape(role: ZenimeRole): CutCornerShape = when (role) {
    // potong kiri-atas & kanan-bawah
    ZenimeRole.DEVELOPER -> CutCornerShape(
        topStart = 5.dp, topEnd = 0.dp, bottomEnd = 5.dp, bottomStart = 0.dp
    )
    // potong keempat sudut (oktagon)
    ZenimeRole.ADMIN -> CutCornerShape(4.dp)
    // potong kanan-atas & kiri-bawah
    ZenimeRole.MODERATOR -> CutCornerShape(
        topStart = 0.dp, topEnd = 5.dp, bottomEnd = 0.dp, bottomStart = 5.dp
    )
}

private fun roleBadgeIcon(role: ZenimeRole): ImageVector = when (role) {
    ZenimeRole.DEVELOPER -> Icons.Filled.Code
    ZenimeRole.ADMIN -> Icons.Filled.VerifiedUser
    ZenimeRole.MODERATOR -> Icons.Filled.Visibility
}

private fun roleBadgeLabel(role: ZenimeRole): String = when (role) {
    ZenimeRole.DEVELOPER -> "DEVELOPER"
    ZenimeRole.ADMIN -> "ADMIN"
    ZenimeRole.MODERATOR -> "MODERATOR"
}

private val RoleBadgeOutline = Color.Black.copy(alpha = 0.65f)

@Composable
fun RoleBadgeChip(
    info: RoleBadgeInfo,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    height: Dp? = null
) {
    val shape = roleBadgeShape(info.role)
    // Teks gelap di warna neon terang; kalau badge_color custom-nya gelap, putih.
    val avgLum = (info.primary.luminance() + info.secondary.luminance()) / 2f
    val content = if (avgLum > 0.2f) Color(0xFF0B0B0F) else Color.White

    // Kilau cuma buat Developer (role paling tinggi).
    val shineState: State<Float>? = if (animated && info.role == ZenimeRole.DEVELOPER) {
        val transition = rememberInfiniteTransition(label = "roleShine")
        transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "roleShinePhase"
        )
    } else {
        null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(info.primary, info.secondary))
            )
            .border(width = 1.dp, color = RoleBadgeOutline, shape = shape)
            .drawWithContent {
                drawContent()
                if (shineState != null) {
                    // dibaca di fase draw, jadi gak recompose tiap frame
                    val x = size.width * shineState.value
                    val half = size.width * 0.14f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0f)
                            ),
                            start = Offset(x - half, 0f),
                            end = Offset(x + half, size.height)
                        )
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = if (height != null) 0.dp else 2.dp)
    ) {
        Icon(
            imageVector = roleBadgeIcon(info.role),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = roleBadgeLabel(info.role),
            color = content,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
        )
    }
}
