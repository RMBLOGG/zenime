package com.example.ui.screens.announcement

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.AnnouncementPopup
import kotlinx.coroutines.delay

/**
 * Pop up pengumuman in-app yang dikontrol dari Firebase Remote Config
 * (lihat RemoteConfigManager.currentPopup()).
 *
 * Desain: kartu flat bernuansa aksen Zenime (tint crimson tipis + border
 * aksen), header ikon lonceng + judul rata kiri + tombol X, isi rata kiri,
 * baris info kecil berwarna (popup_footnote), tombol pill.
 *
 * Tampil kalau:
 *  - [popup] != null (saklar "popup_enabled" ON di Console), DAN
 *  - [lastSeenId] sudah kebaca (null = DataStore masih loading, ditahan
 *    dulu biar gak "kedip"). Isinya id popup yang terakhir ditutup: dari
 *    DataStore (permanen) atau dari memori sesi kalau popup_repeat = true, DAN
 *  - popup.id BEDA dari [lastSeenId].
 *
 * Dirender sebagai OVERLAY di window yang sama (bukan Dialog), jadi scrim
 * ikut beranimasi. Panggil di dalam Box(fillMaxSize) DI ATAS konten app.
 * Kalau saklar di-OFF-in dari Console pas popup kebuka, popup ikut animasi
 * keluar sendiri.
 *
 * Animasi (sengaja halus): scrim fade, kartu masuk (spring scale + slide +
 * fade), konten muncul berurutan, lonceng goyang sekali pas muncul, tombol
 * mengecil sedikit saat ditekan.
 *
 * [onDismiss] dipanggil dengan id popup yang ditutup.
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

    // Masuk = spring (overshoot tipis), keluar = tween cepat. Dipanggil
    // TANPA syarat supaya nilai awalnya 0.
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(durationMillis = 180, easing = FastOutLinearInEasing)
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

    // Sengaja TIDAK menutup popup lewat tombol back -- cuma diserap biar
    // gak nembus nutup Activity/nav di belakangnya. Hanya X yang menutup.
    BackHandler(enabled = visible) { }

    val clamped = progress.coerceIn(0f, 1f)
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim -- cuma nyerap tap biar gak "tembus" ke konten di belakang.
        // Sengaja TIDAK menutup popup; hanya tombol X yang boleh menutup.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = clamped }
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(interactionSource = scrimInteraction, indication = null) { }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)
    // Permukaan gelap Zenime + tint tipis warna aksen -- kartu terasa
    // "berwarna" tapi tetap nyatu sama tema navy-crimson.
    val container = lerp(MaterialTheme.colorScheme.surface, primary, 0.12f)
    val clamped = progress.coerceIn(0f, 1f)
    val cardScale = 0.9f + 0.1f * progress
    val absorbTouch = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = container,
        border = BorderStroke(1.5.dp, primary.copy(alpha = 0.75f)),
        shadowElevation = 16.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = clamped
                scaleX = cardScale
                scaleY = cardScale
                translationY = (1f - clamped) * 24.dp.toPx()
            }
            // Serap tap di kartu biar gak "tembus" ke scrim (yang nutup popup).
            .clickable(interactionSource = absorbTouch, indication = null) { }
    ) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            // Header: lonceng + judul + X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 6.dp, top = 8.dp)
                    .staggerIn(visible, 0),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WigglingBell(visible = visible, tint = primary)
                if (popup.title.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = popup.title,
                        color = onSurface,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Tutup",
                        tint = onSurface
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (popup.message.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = popup.message,
                        color = onSurface.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .staggerIn(visible, 1)
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                if (popup.footnote.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = popup.footnote,
                        color = lerp(primary, Color.White, 0.25f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.staggerIn(visible, 2)
                    )
                }

                Spacer(Modifier.height(18.dp))

                Box(modifier = Modifier.staggerIn(visible, 3)) {
                    PressableButton(
                        text = if (hasAction) popup.buttonText else "Oke",
                        onClick = onPrimary
                    )
                }
            }
        }
    }
}

/** Lonceng yang goyang redam sekali begitu popup muncul. */
@Composable
private fun WigglingBell(visible: Boolean, tint: Color) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            delay(350)
            listOf(16f, -14f, 10f, -6f, 0f).forEach { angle ->
                rotation.animateTo(angle, tween(durationMillis = 90))
            }
        }
    }

    Icon(
        imageVector = Icons.Filled.Notifications,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                rotationZ = rotation.value
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
    )
}

/** Tombol pill Material3 dengan efek "mengecil" saat ditekan. */
@Composable
private fun PressableButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
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
            .height(46.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Muncul berurutan: tiap elemen fade + naik 16dp dengan jeda bertahap
 * berdasarkan [index]. Pas [visible] false langsung reset (animasi keluar
 * ditangani sama kartunya).
 */
@Composable
private fun Modifier.staggerIn(visible: Boolean, index: Int): Modifier {
    val p by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 340,
            delayMillis = if (visible) 100 + index * 60 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "stagger$index"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
    }
}

// Cuma izinkan http/https -- URL datang dari Remote Config, jadi jangan
// sampai bisa nge-trigger skema aneh (intent:, file:, dll).
private fun isSafeUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
