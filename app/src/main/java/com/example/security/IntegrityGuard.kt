package com.example.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Deteksi app terlarang di device:
 *  1. App proxy/MITM (Reqable, HTTP Toolkit, dll) -- lewat PackageManager.
 *  2. Auto clicker -- kata kunci nama app (KEYWORDS) DAN Accessibility Service
 *     aktif yang bisa kirim gesture tap/swipe (detectedGestureService).
 *
 * Deteksi proxy/MITM & kata kunci dicek lewat PackageManager -- dicek di MainActivity.onCreate DAN
 * onResume, SEBELUM checkAppState (jadi sebelum request API apa pun jalan
 * kalau kedeteksi). Lihat MainActivity.kt untuk pemasangannya.
 *
 * Butuh <queries> di AndroidManifest.xml supaya queryIntentActivities bisa
 * lihat app lain di Android 11+ (sudah ditambahkan).
 */
object IntegrityGuard {

    // Dicocokkan (huruf kecil) ke "packageName + label" app yang punya ikon launcher.
    private val KEYWORDS = listOf(
        "reqable",
        "httptoolkit",
        "httpcanary",
        "pcapdroid",
        "remote_capture",   // package PCAPdroid
        "sslcapture",       // Packet Capture
        "mitm",
        "charles",
        "fiddler",
        "wireshark",
        // Auto clicker. JANGAN pakai kata umum kayak "clicker"/"tap"/"macro":
        // game (Clicker Heroes, dll) dan app sah bakal ikut ke-block.
        "autoclick",
        "auto click",
        "autotap",
        "auto tap",
        "klik otomatis",
        "pengklik",
    )

    // Accessibility Service resmi yang boleh aktif walau bisa gesture.
    // App bawaan sistem (FLAG_SYSTEM) sudah otomatis dilewati.
    private val ACCESSIBILITY_WHITELIST = setOf(
        "com.google.android.marvin.talkback",
        "com.android.talkback",
        "com.samsung.android.accessibility.talkback",
        "com.google.android.apps.accessibility.voiceaccess",
    )

    /**
     * Balikin nama app yang terdeteksi, atau null kalau aman.
     * [extraKeywords] boleh diisi dari Firebase Remote Config kalau mau
     * nambah daftar tanpa rilis APK baru (opsional, belum dipakai).
     */
    fun detectedTool(context: Context, extraKeywords: List<String> = emptyList()): String? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        val keys = (KEYWORDS + extraKeywords).map { it.lowercase() }

        for (info in apps) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            val label = info.loadLabel(pm).toString()
            val haystack = "$pkg $label".lowercase()
            if (keys.any { it in haystack }) return label
        }
        return detectedGestureService(context)
    }

    /**
     * Auto clicker butuh Accessibility Service yang boleh dispatch gesture.
     * Cek service aksesibilitas yang lagi AKTIF dan punya kemampuan itu, apa pun
     * nama app-nya. Dilewati: app ini sendiri, app bawaan sistem, dan whitelist
     * (TalkBack, Voice Access). Cuma jalan di Android 8+ (gesture dispatch
     * baru ada di API 26).
     */
    private fun detectedGestureService(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return null
        val pm = context.packageManager

        val services = try {
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        } catch (e: Exception) {
            return null
        }

        for (info in services) {
            val serviceInfo = info.resolveInfo?.serviceInfo ?: continue
            val pkg = serviceInfo.packageName
            if (pkg == context.packageName || pkg in ACCESSIBILITY_WHITELIST) continue

            val appFlags = serviceInfo.applicationInfo?.flags ?: 0
            val isSystem = appFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem) continue

            val canGesture = info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
            if (canGesture) {
                return serviceInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
            }
        }
        return null
    }
}

@Composable
fun BlockedToolScreen(toolName: String, onExit: () -> Unit) {
    // Tombol back juga cuma keluar -- gak ada jalan balik ke app.
    BackHandler(enabled = true) { onExit() }

    val accent = MaterialTheme.colorScheme.error

    // Satu-satunya animasi: emoji "mental" masuk dengan pantulan kecil.
    val emojiScale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        emojiScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Cahaya lembut dari atas, warnanya ngikut warna error tema.
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.30f),
                        radius = size.width * 0.95f
                    )
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = emojiScale.value
                    scaleY = emojiScale.value
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDDFF", fontSize = 52.sp) // moai
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Woy, ngapain itu?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Mau ngapain sih kocak pakai apk \"$toolName\"? " +
                "Nonton tinggal nonton aja... hapus atau matiin dulu tuh apk-nya.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        ) {
            Text(
                text = "Terdeteksi: $toolName",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(
                text = "Oke, gue keluar dulu",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Udah dihapus atau dimatiin? Buka Zenime lagi, langsung jalan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
