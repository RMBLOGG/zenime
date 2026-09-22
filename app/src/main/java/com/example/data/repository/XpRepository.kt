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
    private val chatRepository: ChatRepository = ChatRepository(),
    private val clanRepository: ClanRepository = ClanRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository()
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

    /**
     * Batch-fetch level buat sekumpulan uid sekaligus -- dipakai badge level
     * kecil di bubble Chat Global (pola sama kayak [ClanRepository.getClanTagsForUids]).
     * User yang belum punya baris di user_xp (belum pernah heartbeat) gak
     * masuk map ini -- pemanggil anggap itu sebagai "belum level up"/gak usah
     * ditampilin badge-nya, bukan Level 1 default.
     */
    suspend fun getLevelsForUids(uids: List<String>): Result<Map<String, Int>> = runCatching {
        val distinctUids = uids.filter { it.isNotBlank() }.distinct()
        if (distinctUids.isEmpty()) return@runCatching emptyMap()

        val filter = "in.(${distinctUids.joinToString(",")})"
        xpApi.getUserXpBatch(firebaseUidIn = filter)
            .associate { it.firebaseUid to it.level }
    }

    suspend fun getLeaderboard(): Result<List<UserXp>> = runCatching {
        xpApi.getLeaderboard()
    }

    /**
     * Top 100 leaderboard (total_xp kumulatif, TIDAK reset), digabung
     * username/avatar dari chat_profiles + tag clan + status Premium.
     *
     * DIBATASIN ke top 100 (limit dari xpApi.getLeaderboard()) -- gak lagi
     * nyakup SEMUA user terdaftar (termasuk yang 0 XP nangkring di bawah
     * kayak sebelumnya). Alasannya dua: (1) diminta biar list-nya gak
     * kepanjangan buat leaderboard yang cuma relevan buat top performer,
     * dan (2) performa -- sebelumnya ini narik profil SEMUA user (bisa
     * ratusan) buat 1 leaderboard, sekarang cuma narik profil buat 100 uid
     * yang beneran tampil, lewat 1 request batch (bukan lagi getAllProfiles
     * yang berat).
     */
    suspend fun getLeaderboardDisplay(): Result<List<UserXpDisplay>> = runCatching {
        val topXp = xpApi.getLeaderboard()
        val relevantUids = topXp.map { it.firebaseUid }

        // Profil, tag clan, & status Premium buat 100 uid ini SEKALIGUS
        // BARENGAN (bukan berurutan) -- masing-masing 1 request batch.
        val profiles = chatRepository.getProfilesForUids(relevantUids)
        val clanTags = clanRepository.getClanTagsForUids(relevantUids).getOrDefault(emptyMap())
        val premiumUids = premiumRepository.getPremiumStatusForUids(relevantUids)

        topXp.map { xp ->
            val profile = profiles[xp.firebaseUid]
            UserXpDisplay(
                firebaseUid = xp.firebaseUid,
                totalXp = xp.totalXp,
                level = xp.level,
                username = profile?.username?.ifBlank { "Pengguna" } ?: "Pengguna",
                avatarUrl = profile?.avatarUrl,
                clanTag = clanTags[xp.firebaseUid],
                isPremium = premiumUids[xp.firebaseUid] == true
            )
        }.sortedByDescending { it.totalXp }
    }
}
