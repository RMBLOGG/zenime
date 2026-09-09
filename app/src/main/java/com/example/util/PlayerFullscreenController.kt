package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Jembatan kecil antara PlayerScreen (Compose) dan NavGraph buat nentuin
 * mode fullscreen player -- mirip pola [PipController]. PlayerScreen dibuka
 * secara default dalam mode PORTRAIT (video kecil di atas, info + episode
 * list bisa di-scroll di bawahnya, status/nav bar tetap kelihatan). Baru
 * pindah ke LANDSCAPE + immersive (status/nav bar disembunyiin) kalau user
 * pencet tombol fullscreen atau device di-rotate ke landscape.
 *
 * Sengaja dibikin object (bukan state di dalam PlayerScreen) karena
 * orientasi & immersive mode di-drive dari NavGraph berdasarkan current
 * route/backstack entry (satu sumber kebenaran) -- lihat komentar di
 * NavGraph.kt soal race kondisi pas transisi "Episode Selanjutnya". Kalau
 * flag ini ditaruh di dalam composable PlayerScreen, NavGraph gak akan bisa
 * ikut baca perubahannya secara sinkron dari luar.
 */
object PlayerFullscreenController {
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }
}
