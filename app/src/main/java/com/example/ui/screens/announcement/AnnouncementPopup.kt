package com.example.ui.screens.announcement

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.AnnouncementPopup
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeOnSurfaceVariantDark

/**
 * Pop up pengumuman in-app yang dikontrol dari Firebase Remote Config
 * (lihat RemoteConfigManager.currentPopup()).
 *
 * Tampil kalau:
 *  - [popup] != null (saklar "popup_enabled" ON di Console), DAN
 *  - [lastSeenId] sudah kebaca dari DataStore (null = masih loading,
 *    ditahan dulu biar gak "kedip"), DAN
 *  - popup.id BEDA dari [lastSeenId] (user belum pernah nutup popup ini).
 *
 * Dirender sebagai OVERLAY di dalam window yang sama (bukan Dialog), jadi
 * scrim-nya ikut beranimasi. Panggil di dalam Box(fillMaxSize) DI ATAS
 * konten utama app. Kalau saklar di-OFF-in dari Console pas popup lagi
 * kebuka, popup ikut animasi keluar sendiri.
 *
 * Animasi: scrim fade, kartu masuk (spring scale + slide + fade), konten
 * muncul berurutan (stagger), cincin denyut + ikon "bernapas" di header,
 * tombol punya efek tekan.
 *
 * [onDismiss] dipanggil dengan id popup yang ditutup -- simpan ke
 * DataStore supaya gak muncul lagi.
 */
@Composable
fun AnnouncementPopupHost(
    popup: AnnouncementPopup?,
    lastSeenId: String?,
    onDismiss: (String) -> Unit
) {
    val active = popup != null && lastSeenId != null && popup.id != lastSeenId

    // displayed = popup yang lagi dirender (ditahan sampai animasi keluar
    // kelar, meski popup di Remote Config udah null). visible = target animasi.
    var displayed by remember { mutableStateOf<AnnouncementPopup?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(active, popup) {
        if (active) {
            displayed = popup
            visible = true
        } else {
            visible = false
        }
    }

    // Masuk = spring (ada sedikit overshoot biar terasa hidup), keluar =
    // tween cepat. Dipanggil TANPA syarat supaya nilai awalnya 0.
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 200, easing = FastOutLinearInEasing)
        },
        label = "announcementProgress",
        finishedListener = { if (!visible) displayed = null }
    )

    val current = displayed ?: return

    val context = LocalContext.current
    val hasAction = current.buttonText.isNotEmpty() && isSafeUrl(current.buttonUrl)

    val dismiss: () -> Unit = {
        if (visible) {
            visible = false
            onDismiss(current.id)
        }
    }
    val openAction: () -> Unit = {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(current.buttonUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Gak ada app yang bisa buka link -- abaikan, popup tetap ditutup.
        }
        dismiss()
    }

    BackHandler(enabled = visible) { dismiss() }

    val clamped = progress.coerceIn(0f, 1f)
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim -- tap di luar kartu = tutup.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = clamped }
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(interactionSource = scrimInteraction, indication = null) { dismiss() }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            PopupCard(
                popup = current,
                visible = visible,
                progress = progress,
                hasAction = hasAction,
                onPrimary = if (hasAction) openAction else dismiss,
                onClose = dismiss
            )
        }
    }
}

@Composable
private fun PopupCard(
    popup: AnnouncementPopup,
    visible: Boolean,
    progress: Float,
    hasAction: Boolean,
    onPrimary: () -> Unit,
    onClose: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)
    val clamped = progress.coerceIn(0f, 1f)
    val cardScale = 0.86f + 0.14f * progress
    val absorbTouch = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 24.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = clamped
                scaleX = cardScale
                scaleY = cardScale
                translationY = (1f - clamped) * 32.dp.toPx()
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(primary.copy(alpha = 0.55f), CardOutlineBorder)
                ),
                shape = shape
            )
            // Serap tap di kartu biar gak "tembus" ke scrim (yang nutup popup).
            .clickable(interactionSource = absorbTouch, indication = null) { }
    ) {
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PopupHeader(primary = primary)

                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Chip label
                    Surface(
                        shape = CircleShape,
                        color = primary.copy(alpha = 0.14f),
                        modifier = Modifier.staggerIn(visible, 0)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(primary)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "PENGUMUMAN",
                                color = primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    if (popup.title.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = popup.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.staggerIn(visible, 1)
                        )
                    }

                    if (popup.message.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = popup.message,
                            color = ZenimeOnSurfaceVariantDark,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .staggerIn(visible, 2)
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.staggerIn(visible, 3),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PressableButton(
                            text = if (hasAction) popup.buttonText else "Oke",
                            onClick = onPrimary
                        )
                        if (hasAction) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onClose) {
                                Text(
                                    text = "Tutup",
                                    color = ZenimeOnSurfaceVariantDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Tutup",
                    tint = ZenimeOnSurfaceVariantDark
                )
            }
        }
    }
}

/** Header: glow crimson + cincin denyut + ikon megafon yang "bernapas". */
@Composable
private fun PopupHeader(primary: Color) {
    val infinite = rememberInfiniteTransition(label = "announcementPulse")
    val ringA by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ringA"
    )
    val ringB by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            initialStartOffset = StartOffset(1300)
        ),
        label = "ringB"
    )
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.55f),
                        radius = size.width * 0.7f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val base = 34.dp.toPx()
                val extra = 34.dp.toPx()
                listOf(ringA, ringB).forEach { t ->
                    drawCircle(
                        color = primary.copy(alpha = (1f - t) * 0.35f),
                        radius = base + extra * t,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = breathe
                        scaleY = breathe
                    }
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = primary,
                        spotColor = primary
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(primary, lerp(primary, Color.Black, 0.35f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/** Tombol pill Material3 dengan efek "mengecil" saat ditekan. */
@Composable
private fun PressableButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    Button(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Muncul berurutan: tiap elemen fade + naik 24dp dengan jeda bertahap
 * berdasarkan [index]. Pas [visible] false langsung reset (animasi keluar
 * ditangani sama kartunya).
 */
@Composable
private fun Modifier.staggerIn(visible: Boolean, index: Int): Modifier {
    val p by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 380,
            delayMillis = if (visible) 120 + index * 70 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "stagger$index"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
    }
}

// Cuma izinkan http/https -- URL datang dari Remote Config, jadi jangan
// sampai bisa nge-trigger skema aneh (intent:, file:, dll).
private fun isSafeUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
