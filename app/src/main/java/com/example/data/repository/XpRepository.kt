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
     * Leaderboard (total_xp kumulatif, TIDAK reset -- fungsinya sama kayak
     * sebelumnya) -- digabung username/avatar dari chat_profiles DAN tag
     * clan (buat chip di desain podium/list), mencakup SEMUA user yang
     * tercatat di chat_profiles (bukan cuma yang udah punya baris di
     * user_xp). User yang belum pernah nonton otomatis 0 XP/Level 1 dan
     * nangkring di bawah, bukan hilang dari daftar.
     *
     * Catatan: "semua user" di sini terbatas ke yang udah tercatat di
     * chat_profiles (kebentuk begitu user buka Profil/Chat minimal sekali) --
     * gak ada tabel "semua user terdaftar" tersendiri yang bisa dibaca lewat
     * PostgREST (daftar user Firebase Auth sendiri gak bisa di-query dari
     * client), jadi ini proxy terbaik yang ada.
     */
    suspend fun getLeaderboardDisplay(): Result<List<UserXpDisplay>> = runCatching {
        val profiles = chatRepository.getAllProfiles()
        val xpByUid = xpApi.getLeaderboard().associateBy { it.firebaseUid }

        // Tag clan & status Premium CUMA dicek buat uid yang punya baris di
        // user_xp (yang beneran nangkring di leaderboard, dibatasin ~100
        // teratas -- lihat limit di ZenimeXpApi.getLeaderboard), BUKAN buat
        // SEMUA user terdaftar.
        //
        // Sebelumnya ini ngecek premium SATU-SATU ke server buat tiap profil
        // (bisa ratusan user, termasuk yang 0 XP di paling bawah dan gak
        // kepake badge-nya), padahal koneksi HTTP cuma ngizinin ~5 request
        // bareng ke host yang sama -- sisanya ngantre. Itu yang bikin
        // Leaderboard XP muter lama banget pas dibuka.
        val relevantUids = xpByUid.keys.toList()
        val clanTags = clanRepository.getClanTagsForUids(relevantUids).getOrDefault(emptyMap())
        val premiumUids = premiumRepository.getPremiumStatusForUids(relevantUids)

        profiles.map { profile ->
            val xp = xpByUid[profile.firebaseUid]
            UserXpDisplay(
                firebaseUid = profile.firebaseUid,
                totalXp = xp?.totalXp ?: 0L,
                level = xp?.level ?: 1,
                username = profile.username.ifBlank { "Pengguna" },
                avatarUrl = profile.avatarUrl,
                clanTag = clanTags[profile.firebaseUid],
                isPremium = premiumUids[profile.firebaseUid] == true
            )
        }.sortedByDescending { it.totalXp }
    }
}
