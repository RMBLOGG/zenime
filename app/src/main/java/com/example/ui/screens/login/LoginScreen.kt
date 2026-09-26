package com.example.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.util.findActivity

/**
 * Gerbang wajib login sebelum masuk ke app. Ditaruh sebagai start
 * destination di NavGraph kalau belum ada sesi Firebase aktif -- lihat
 * ZenimeAppNavHost.
 *
 * Backdrop-nya grid poster anime dari LoginBackdropPosters (daftar tetap,
 * BUKAN dari endpoint /home) -- jadi langsung tampil dari detik pertama,
 * gak nunggu network sama sekali, termasuk buat user yang baru install.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val posterUrls = viewModel.posterUrls
    val context = LocalContext.current

    // Sembunyiin navigation bar sistem selama di halaman ini biar backdrop
    // poster-nya full-bleed sampai bawah layar. Dikembaliin lagi begitu user
    // pindah dari layar ini (login sukses / activity di-dispose) -- pola
    // yang sama kayak immersive mode di PlayerScreen.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZenimeBackgroundDark)
    ) {
        // Layer 1: grid poster tetap (LoginBackdropPosters) yang diputar
        // miring, statis (bukan buat discroll). Selalu ada isinya dari
        // render pertama -- gak ada shimmer/kosong nunggu network lagi.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -9f
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
        ) {
            items(posterUrls) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        // Layer 2: gradient gelap -- dibiarin jernih di 2/3 atas biar poster
        // keliatan jelas kayak referensi, cuma nge-dim di area bawah tempat
        // teks & tombol duduk.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.72f to ZenimeBackgroundDark.copy(alpha = 0.55f),
                            0.88f to ZenimeBackgroundDark.copy(alpha = 0.92f),
                            1.0f to ZenimeBackgroundDark
                        )
                    )
                )
        )

        // Layer 3: wordmark logo di pojok kiri atas, seukuran wordmark Crunchyroll
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.zenime_wordmark),
            contentDescription = "Zenime",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
                .height(40.dp)
        )

        // Layer 4: judul + tombol login, nempel di bawah
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Semua anime favoritmu.\nSemua di satu tempat.",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Login dulu buat lanjut nonton anime favorit kamu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = {
                    viewModel.signInWithGoogle(context) { onLoginSuccess() }
                },
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardOutlineBorder)
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Menghubungkan...")
                } else {
                    Text("G", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Masuk dengan Google")
                }
            }

            if (loginError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = loginError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
