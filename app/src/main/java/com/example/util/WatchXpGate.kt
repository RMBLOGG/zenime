package com.example.util

import android.os.SystemClock
import androidx.media3.common.Player

/**
 * Anti-curang XP nonton (sisi client). Dua lapis:
 *
 * 1. [PlaybackCoordinator] -- cuma SATU video yang boleh muter dalam satu waktu.
 *    Begitu ada player yang mulai playing, player lain (mini player, jendela
 *    floating/multi-window, player donghua vs anime) otomatis di-pause.
 * 2. [WatchXpGate] -- XP dihitung dari waktu nonton AKTIF global, bukan per
 *    player: cuma satu "pemilik" yang boleh nambah hitungan, dan maksimal 1
 *    heartbeat per ~55 detik waktu asli, gak peduli berapa player/jendela
 *    yang lagi hidup di proses ini.
 *
 * Ini cuma lapisan client. Batas yang sebenarnya WAJIB ada di server
 * (Edge Function `watch-xp-heartbeat`), karena request bisa dipalsukan.
 */
object PlaybackCoordinator {
    private val players = LinkedHashMap<Player, Player.Listener>()

    /** Idempotent: aman dipanggil berkali-kali untuk player yang sama. */
    @Synchronized
    fun attach(player: Player) {
        if (players.containsKey(player)) return
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) pauseOthers(player)
            }
        }
        players[player] = listener
        player.addListener(listener)
    }

    /** Wajib dipanggil SEBELUM player.release(). */
    @Synchronized
    fun detach(player: Player) {
        players.remove(player)?.let { player.removeListener(it) }
    }

    private fun pauseOthers(active: Player) {
        val others = synchronized(this) { players.keys.filter { it !== active } }
        others.forEach { if (it.isPlaying) it.pause() }
    }
}

object WatchXpGate {
    private const val OWNER_TIMEOUT_MS = 3_000L
    private const val HEARTBEAT_SECONDS = 60
    private const val MIN_GRANT_INTERVAL_MS = 55_000L

    private var owner: Any? = null
    private var ownerLastTickAt = 0L
    private var seconds = 0
    private var lastGrantAt: Long? = null

    /**
     * Dipanggil player TIAP DETIK selama video beneran playing.
     * @return true kalau sekarang boleh kirim satu heartbeat 1 menit.
     */
    @Synchronized
    fun onActiveSecond(token: Any): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (owner !== token) {
            // Ada player lain yang masih aktif nambah hitungan -> yang ini gak dihitung.
            if (owner != null && now - ownerLastTickAt < OWNER_TIMEOUT_MS) return false
            owner = token
        }
        ownerLastTickAt = now

        seconds++
        if (seconds < HEARTBEAT_SECONDS) return false
        seconds = 0

        val last = lastGrantAt
        if (last != null && now - last < MIN_GRANT_INTERVAL_MS) return false
        lastGrantAt = now
        return true
    }

    /** Dipanggil pas player dilepas / di-minimize, biar player lain bisa jadi pemilik. */
    @Synchronized
    fun release(token: Any) {
        if (owner === token) owner = null
    }
}
