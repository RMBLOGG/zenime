package com.example.data.api

import com.example.data.model.UserXp
import com.example.data.model.UserXpMonthly
import com.example.data.model.WatchXpHeartbeatRequest
import com.example.data.model.WatchXpHeartbeatResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit khusus fitur XP nonton, dipisah dari [ZenimeSupabaseApi]
 * kayak pola [ZenimeClanApi]. Base URL & OkHttpClient numpang [SupabaseNetworkModule].
 *
 * PENTING: [sendWatchHeartbeat] manggil Edge Function `watch-xp-heartbeat`
 * yang WAJIB dapet header Authorization isinya Firebase ID TOKEN ASLI (bukan
 * anon key) -- lihat [com.example.data.repository.XpRepository.authHeader].
 * Edge Function itu yang nentuin firebase_uid dari token terverifikasi, JANGAN
 * pernah nambahin firebase_uid ke body request ini.
 */
interface ZenimeXpApi {

    // --- Aksi (Edge Function, verifikasi Firebase ID Token) ---

    @POST("functions/v1/watch-xp-heartbeat")
    suspend fun sendWatchHeartbeat(
        @Header("Authorization") authorization: String,
        @Body body: WatchXpHeartbeatRequest
    ): WatchXpHeartbeatResponse

    // --- Baca langsung lewat PostgREST (public SELECT, RLS deny-all buat write) ---

    @GET("rest/v1/user_xp")
    suspend fun getUserXp(
        @Query("firebase_uid") firebaseUidEq: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 1
    ): List<UserXp>

    /** Batch-fetch level buat sekumpulan uid sekaligus -- dipakai badge level di bubble Chat Global. */
    @GET("rest/v1/user_xp")
    suspend fun getUserXpBatch(
        @Query("firebase_uid") firebaseUidIn: String,
        @Query("select") select: String = "firebase_uid,level"
    ): List<UserXp>

    @GET("rest/v1/user_xp")
    suspend fun getLeaderboard(
        @Query("select") select: String = "*",
        @Query("order") order: String = "total_xp.desc",
        @Query("limit") limit: Int = 100
    ): List<UserXp>

    /**
     * Leaderboard BULANAN: XP nonton yang dikumpulin selama satu bulan (WIB),
     * dicatat trigger `trg_monthly_xp` di tabel user_xp_monthly. [periodEq]
     * formatnya "eq.2026-09". Ganti bulan = baris baru, jadi otomatis mulai
     * dari 0 tanpa ada yang dihapus. Level TETAP dari user_xp, gak kesentuh.
     */
    @GET("rest/v1/user_xp_monthly")
    suspend fun getMonthlyLeaderboard(
        @Query("period") periodEq: String,
        @Query("select") select: String = "firebase_uid,xp",
        @Query("order") order: String = "xp.desc",
        @Query("limit") limit: Int = 100
    ): List<UserXpMonthly>
}
