package com.example.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Deteksi app proxy/MITM (Reqable, HTTP Toolkit, dll) yang terpasang di
 * device lewat PackageManager -- dicek di MainActivity.onCreate DAN
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
        return null
    }
}

@Composable
fun BlockedToolScreen(toolName: String, onExit: () -> Unit) {
    // Tombol back juga cuma keluar -- gak ada jalan balik ke app.
    BackHandler(enabled = true) { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Aplikasi Terdeteksi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Aplikasi \"$toolName\" terdeteksi di perangkat Anda. " +
                "Aplikasi ini tidak diizinkan karena dapat mengganggu keamanan dan fungsi aplikasi.\n\n" +
                "Harap uninstall aplikasi tersebut untuk melanjutkan.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Keluar Aplikasi")
        }
    }
}
