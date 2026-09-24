package com.example.util

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Jembatan antara PlayerScreen (Compose) dan mini player yang ngambang di
 * layar lain (Home, Search, dll) -- mirip [PipController] tapi buat PiP
 * DALAM APLIKASI sendiri (bukan PiP sistem Android), lihat MiniPlayerBar.kt.
 *
 * Cara kerjanya: PlayerScreen NORMALNYA bikin & nge-release ExoPlayer sendiri
 * tiap kali layar itu dibuka/ditutup (lihat `remember { ExoPlayer.Builder... }`
 * dan `onDispose { exoPlayer.release() }` di PlayerScreen.kt). Biar video
 * TETEP jalan pas user minimize (keluar dari PlayerScreen tapi video-nya
 * gak berhenti), ExoPlayer instance yang lagi jalan itu "dititipin" ke sini
 * (bukan di-release) -- MiniPlayerBar yang nampilinnya jadi jendela kecil di
 * layar lain. Begitu user balik ke PlayerScreen (tap mini player ATAU lewat
 * tombol episode lagi buat episode yang SAMA), PlayerScreen "ngambil balik"
 * instance yang sama ini (consume) alih-alih bikin ExoPlayer baru dari nol
 * -- posisi & buffer yang udah ada tetap kepake, gak restart dari awal.
 */
object MiniPlayerController {

    data class MiniPlayerState(
        val exoPlayer: ExoPlayer,
        val episodeId: String,
        val animeId: String,
        val animeTitle: String,
        val episodeLabel: String,
        val posterUrl: String?
    )

    private val _state = MutableStateFlow<MiniPlayerState?>(null)
    val state: StateFlow<MiniPlayerState?> = _state

    /**
     * Dipanggil PlayerScreen pas user pencet tombol "minimize". Kalau
     * sebelumnya udah ada sesi mini player lain yang MASIH nyala (ExoPlayer
     * instance beda, berarti episode lain yang belum sempet di-expand lagi),
     * itu di-release dulu -- gak boleh ada 2 ExoPlayer nyala bareng, boros
     * resource & bisa tabrakan audio.
     */
    fun minimize(
        exoPlayer: ExoPlayer,
        episodeId: String,
        animeId: String,
        animeTitle: String,
        episodeLabel: String,
        posterUrl: String?
    ) {
        val previous = _state.value
        if (previous != null && previous.exoPlayer !== exoPlayer) {
            previous.exoPlayer.release()
        }
        _state.value = MiniPlayerState(exoPlayer, episodeId, animeId, animeTitle, episodeLabel, posterUrl)
    }

    /**
     * Dipanggil PlayerScreen pas baru dibuka (dari nol / tap "Episode
     * Selanjutnya" / tap mini player). Kalau episode yang mau diputer SAMA
     * persis kayak yang lagi ngambang di mini player, "ambil balik"
     * ExoPlayer-nya (posisi & buffer kepake lagi, gak reprepare) dan bersihin
     * state mini player (jendelanya otomatis ilang). Kalau beda episode,
     * return null -- PlayerScreen bikin ExoPlayer baru seperti biasa (dan
     * sesi mini player lama, kalau ada, TETAP jalan di background sampai
     * user nutup/minimize-nya sendiri).
     */
    fun consume(episodeId: String): ExoPlayer? {
        val current = _state.value ?: return null
        if (current.episodeId != episodeId) return null
        _state.value = null
        return current.exoPlayer
    }

    /** Tombol "X" di mini player -- stop & lepas resource-nya beneran. */
    fun close() {
        _state.value?.exoPlayer?.release()
        _state.value = null
    }
}
