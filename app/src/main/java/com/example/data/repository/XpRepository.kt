package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeXpApi
import com.example.data.model.UserXp
import com.example.data.model.UserXpDisplay
import com.example.data.model.WatchXpHeartbeatRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Repository fitur XP nonton (leveling ala Aniku: 45 XP/menit nonton, level
 * makin tinggi butuh XP makin banyak).
 *
 * [sendHeartbeat] WAJIB nempelin Firebase ID Token asli sebagai header
 * Authorization -- Edge Function `watch-xp-heartbeat` ambil firebase_uid dari
 * token yang diverifikasi server, BUKAN dari body request. Jangan pernah
 * nambahin firebase_uid ke body/parameter fungsi ini; itu justru celah yang
 * bikin Aniku kena hack dulu (client dipercaya kirim identitasnya sendiri).
 *
 * Baca leaderboard & XP sendiri langsung lewat PostgREST -- RLS-nya public
 * SELECT tapi deny-all buat INSERT/UPDATE/DELETE (lihat SQL schema-nya), jadi
 * gak butuh token khusus buat baca.
 */
class XpRepository(
    private val xpApi: ZenimeXpApi = SupabaseNetworkModule.xpApi,
    private val chatRepository: ChatRepository = ChatRepository()
) {

    private suspend fun authHeader(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Kamu harus login dulu")
        val token = user.getIdToken(false).await()?.token
            ?: throw IllegalStateException("Gagal ambil Firebase ID Token, coba login ulang")
        return "Bearer $token"
    }

    /**
     * Kirim heartbeat "udah nonton [minutes] menit" ke server. Dipanggil dari
     * PlayerScreen tiap ~60 detik SELAMA video beneran playing (bukan pause/
     * buffering/di-minimize) -- lihat [com.example.ui.screens.player.PlayerViewModel.sendWatchHeartbeat].
     * Server sendiri yang cap durasi & jarak antar-panggilan (lihat RPC
     * add_watch_xp), jadi gagal kirim dari sini sifatnya silent/best-effort,
     * gak perlu diretry agresif -- kehilangan satu heartbeat cuma berarti
     * kehilangan sedikit XP, bukan hal kritis.
     */
    suspend fun sendHeartbeat(minutes: Int): Result<Unit> = runCatching {
        xpApi.sendWatchHeartbeat(authHeader(), WatchXpHeartbeatRequest(minutes))
        Unit
    }

    suspend fun getMyXp(firebaseUid: String): Result<UserXp?> = runCatching {
        xpApi.getUserXp(firebaseUidEq = "eq.$firebaseUid").firstOrNull()
    }

    suspend fun getLeaderboard(): Result<List<UserXp>> = runCatching {
        xpApi.getLeaderboard()
    }

    /** Sama kayak [getLeaderboard], tapi digabung username/avatar dari chat_profiles buat dirender di UI. */
    suspend fun getLeaderboardDisplay(): Result<List<UserXpDisplay>> = runCatching {
        val entries = xpApi.getLeaderboard()
        val profiles = entries.map { it.firebaseUid }.distinct()
            .mapNotNull { uid -> chatRepository.getProfile(uid)?.let { uid to it } }
            .toMap()
        entries.map { entry ->
            val profile = profiles[entry.firebaseUid]
            UserXpDisplay(
                firebaseUid = entry.firebaseUid,
                totalXp = entry.totalXp,
                level = entry.level,
                username = profile?.username ?: "Pengguna",
                avatarUrl = profile?.avatarUrl
            )
        }
    }
}
