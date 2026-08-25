package com.example.ui.screens.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Username SociaBuzz dari script "Button on Website" (sbBoW.draw("dayynime", ...)).
// Kalau username/slug donasinya beda, tinggal ganti di sini.
private const val SOCIABUZZ_USERNAME = "dayynime"
private const val SOCIABUZZ_URL = "https://sociabuzz.com/$SOCIABUZZ_USERNAME/tribe"

/**
 * Versi native dari widget donasi SociaBuzz "Button on Website".
 * Script aslinya (sbBoW.draw(...)) cuma jalan di halaman web lewat JS,
 * jadi di Android kita ganti jadi FAB yang buka halaman donasi SociaBuzz
 * lewat browser/Custom Tab bawaan device.
 */
@Composable
fun DonationFab(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    FloatingActionButton(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOCIABUZZ_URL))
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Tidak ada browser untuk membuka link donasi", Toast.LENGTH_SHORT).show()
            }
        },
        containerColor = Color(0xFFFF0000), // sesuai warna tombol di script: "#ff0000"
        contentColor = Color(0xFFFFFFFF),   // sesuai warna icon di script: "#FFFFFF"
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Dukung Dayynime di SociaBuzz",
            modifier = Modifier.size(24.dp)
        )
    }
}
