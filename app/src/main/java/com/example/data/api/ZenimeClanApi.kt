package com.example.data.api

import com.example.data.model.ClanActionResponse
import com.example.data.model.ClanIdRequest
import com.example.data.model.ClanMember
import com.example.data.model.Clan
import com.example.data.model.ClanDonationLogRow
import com.example.data.model.CreateClanRequest
import com.example.data.model.DonateToClanRequest
import com.example.data.model.KickMemberRequest
import com.example.data.model.MyJoinRequestStatusResponse
import com.example.data.model.PendingJoinRequestsResponse
import com.example.data.model.RespondJoinRequestBody
import com.example.data.model.UpdateClanSettingsRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit khusus fitur Clan, dipisah dari [ZenimeSupabaseApi] biar
 * gak perlu ubah file itu. Base URL & OkHttpClient-nya tetap numpang ke
 * [SupabaseNetworkModule] (lihat `clanApi` di situ).
 *
 * PENTING: endpoint di bawah yang manggil Edge Function (`functions/v1/...`)
 * butuh header Authorization isinya Firebase ID TOKEN ASLI (bukan anon key)
 * -- lihat [com.example.data.repository.ClanRepository.authHeader]. Anon
 * key tetap dikirim otomatis lewat interceptor `apikey` bawaan
 * [SupabaseNetworkModule], jadi di sini cuma perlu nambahin Authorization.
 */
interface ZenimeClanApi {

    // --- Aksi (Edge Function, verifikasi Firebase ID Token) ---

    @POST("functions/v1/zenime-clan-create")
    suspend fun createClan(
        @Header("Authorization") authorization: String,
        @Body body: CreateClanRequest
    ): ClanActionResponse

    @POST("functions/v1/zenime-clan-join-request")
    suspend fun submitJoinRequest(
        @Header("Authorization") authorization: String,
        @Body body: ClanIdRequest
    ): ClanActionResponse

    @GET("functions/v1/zenime-clan-my-request-status")
    suspend fun getMyJoinRequestStatus(
        @Header("Authorization") authorization: String,
        @Query("clan_id") clanId: String
    ): MyJoinRequestStatusResponse

    @POST("functions/v1/zenime-clan-respond-request")
    suspend fun respondJoinRequest(
        @Header("Authorization") authorization: String,
        @Body body: RespondJoinRequestBody
    ): ClanActionResponse

    @GET("functions/v1/zenime-clan-pending-requests")
    suspend fun getPendingJoinRequests(
        @Header("Authorization") authorization: String,
        @Query("clan_id") clanId: String
    ): PendingJoinRequestsResponse

    @POST("functions/v1/zenime-clan-donate")
    suspend fun donateToClan(
        @Header("Authorization") authorization: String,
        @Body body: DonateToClanRequest
    ): ClanActionResponse

    @POST("functions/v1/zenime-clan-settings")
    suspend fun updateClanSettings(
        @Header("Authorization") authorization: String,
        @Body body: UpdateClanSettingsRequest
    ): ClanActionResponse

    @POST("functions/v1/zenime-clan-kick")
    suspend fun kickMember(
        @Header("Authorization") authorization: String,
        @Body body: KickMemberRequest
    ): ClanActionResponse

    // --- Baca langsung lewat PostgREST (public SELECT, lihat RLS di SQL, gak butuh Firebase token) ---

    @GET("rest/v1/clans")
    suspend fun getClanById(
        @Query("id") idEq: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 1
    ): List<Clan>

    @GET("rest/v1/clans")
    suspend fun browseClans(
        @Query("select") select: String = "*",
        @Query("order") order: String = "level.desc,total_xp.desc",
        @Query("limit") limit: Int = 50
    ): List<Clan>

    @GET("rest/v1/clan_members")
    suspend fun getClanMembers(
        @Query("clan_id") clanIdEq: String,
        @Query("select") select: String = "clan_id,firebase_uid,role,total_contribution,joined_at",
        @Query("order") order: String = "total_contribution.desc",
        @Query("limit") limit: Int = 500
    ): List<ClanMember>

    /** Cek clan yang lagi diikutin user (kalau ada) -- dipakai buat validasi "udah gabung clan lain". */
    @GET("rest/v1/clan_members")
    suspend fun getMembershipByUid(
        @Query("firebase_uid") firebaseUidEq: String,
        @Query("select") select: String = "clan_id,firebase_uid,role,total_contribution,joined_at",
        @Query("limit") limit: Int = 1
    ): List<ClanMember>

    @GET("rest/v1/clan_donation_log")
    suspend fun getDonationLog(
        @Query("clan_id") clanIdEq: String,
        @Query("created_at") createdAtGte: String,
        @Query("select") select: String = "firebase_uid,amount,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 1000
    ): List<ClanDonationLogRow>
}
