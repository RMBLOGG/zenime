package com.example.util

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nyimpen SATU instance ExoPlayer yang lagi "diminimize" dari PlayerScreen
 * (user pencet back sambil masih nonton), biar videonya tetep jalan dalam
 * bentuk mini player yang ngambang di atas layar lain (Home, Detail, dll).
 *
 * BEDA sama PipController.kt -- itu PiP sistem Android (keluar dari app,
 * ngambang di atas app LAIN). Ini murni in-app, cuma aktif selama user
 * masih di dalam Zenime.
 *
 * Singleton object (bukan ViewModel) karena harus survive lintas
 * NavBackStackEntry -- ExoPlayer-nya dibuat di dalam PlayerScreen (scoped
 * ke backstack entry si route Player), sedangkan mini player dirender di
 * level ZenimeAppNavHost yang lebih tinggi.
 */
object MiniPlayerManager {

    data class MiniPlayerInfo(
        val episodeId: String,
        val animeId: String,
        val animeTitle: String,
        val episodeLabel: String,
        val posterUrl: String?
    )

    private val _info = MutableStateFlow<MiniPlayerInfo?>(null)
    val info: StateFlow<MiniPlayerInfo?> = _info.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var listener: Player.Listener? = null

    /**
     * Dipanggil dari PlayerScreen pas composable-nya di-dispose GARA-GARA
     * user minimize (bukan release biasa). Player yang dikasih di sini
     * TIDAK di-release -- kepemilikannya pindah ke sini.
     */
    fun activate(exoPlayer: ExoPlayer, newInfo: MiniPlayerInfo) {
        // Jaga-jaga kalau ada mini player lain yang masih nyangkut --
        // jarang kejadian, tapi biar gak ada ExoPlayer nganggur.
        if (player != null && player !== exoPlayer) {
            release()
        }

        player = exoPlayer
        _info.value = newInfo
        _isPlaying.value = exoPlayer.isPlaying

        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }
        }
        listener = l
        exoPlayer.addListener(l)
    }

    /**
     * Dipanggil dari PlayerScreen pas dibuka lagi (tap mini player / navigasi
     * balik ke route Player buat episode yang SAMA persis). Kalau match,
     * ExoPlayer yang lagi muter di mini player dipindah balik ke situ tanpa
     * reload/reset posisi. Kalau gak match (beda episode), mini player yang
     * lama dibiarin tetep jalan dan ini balikin null -- PlayerScreen bakal
     * bikin ExoPlayer baru seperti biasa.
     */
    fun consumeForExpand(episodeId: String, animeId: String): ExoPlayer? {
        val currentInfo = _info.value ?: return null
        if (currentInfo.episodeId != episodeId || currentInfo.animeId != animeId) return null

        val p = player
        listener?.let { p?.removeListener(it) }
        listener = null
        player = null
        _info.value = null
        return p
    }

    /** Tombol X di mini player -- tutup total & stop muter. */
    fun release() {
        listener?.let { player?.removeListener(it) }
        listener = null
        player?.release()
        player = null
        _info.value = null
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }
}
