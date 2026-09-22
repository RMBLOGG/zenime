package com.example.data.api

import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageInsert
import com.example.data.model.ChatProfile
import com.example.data.model.ChatProfileUpsert
import com.example.data.model.CoinBalanceResponse
import com.example.data.model.CoinPackagesResponse
import com.example.data.model.PremiumPackagesResponse
import com.example.data.model.PremiumStatusResponse
import com.example.data.model.TopSupportersResponse
import com.example.data.model.ZenimeCodeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface ZenimeSupabaseApi {

    @GET("functions/v1/zenime-list-packages")
    suspend fun getPremiumPackages(): PremiumPackagesResponse

    @POST("functions/v1/zenime-get-code")
    suspend fun getZenimeCode(@Body body: Map<String, String>): ZenimeCodeResponse


    @POST("functions/v1/zenime-check-premium")
    suspend fun checkPremiumStatus(@Body body: Map<String, String>): PremiumStatusResponse

    // --- Top Support (donatur SociaBuzz) ---

    @GET("functions/v1/zenime-top-supporters")
    suspend fun getTopSupporters(): TopSupportersResponse

    // --- ZCoin ---

    @GET("functions/v1/zenime-list-coin-packages")
    suspend fun getCoinPackages(): CoinPackagesResponse

    @POST("functions/v1/zenime-get-coin-balance")
    suspend fun getCoinBalance(@Body body: Map<String, String>): CoinBalanceResponse

    // --- Chat Global ---
    // Dua endpoint di bawah manggil langsung tabel `global_chat_messages`
    // lewat PostgREST bawaan Supabase (bukan Edge Function), jadi cukup
    // tabel + RLS policy-nya dibikin di dashboard (lihat catatan setup).

    @GET("rest/v1/global_chat_messages")
    suspend fun getChatMessages(
        @Query("select") select: String = "id,firebase_uid,username,avatar_url,message,created_at,reply_to_id,reply_to_username,reply_to_message,message_type,audio_url,duration_seconds",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<ChatMessage>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/global_chat_messages")
    suspend fun postChatMessage(@Body body: ChatMessageInsert): List<ChatMessage>

    // Hapus pesan -- filter ganda (id + firebase_uid) di query-nya sendiri, biar
    // dari sisi app cuma bisa hapus pesan yang firebase_uid-nya cocok sama pengirim.
    @DELETE("rest/v1/global_chat_messages")
    suspend fun deleteChatMessage(
        @Query("id") idEq: String,
        @Query("firebase_uid") firebaseUidEq: String
    ): Response<Void>

    // --- Profil Chat (username & avatar custom) ---

    @GET("rest/v1/chat_profiles")
    suspend fun getChatProfile(
        @Query("firebase_uid") firebaseUidEq: String,
        @Query("select") select: String = "firebase_uid,username,avatar_url,banner_url,username_color,user_number,updated_at",
        @Query("limit") limit: Int = 1
    ): List<ChatProfile>

    /** Semua profil user -- basis daftar leaderboard XP biar user yang belum pernah nonton (0 XP) tetap muncul. */
    @GET("rest/v1/chat_profiles")
    suspend fun getAllChatProfiles(
        @Query("select") select: String = "firebase_uid,username,avatar_url,banner_url,username_color,updated_at",
        @Query("limit") limit: Int = 500
    ): List<ChatProfile>

    /**
     * Batch-fetch warna username + user_number (ID urut) + avatar_url
     * TERKINI sekaligus dalam SATU request buat sekumpulan uid -- dipake
     * ngerender warna custom + "#ID" + foto profil di bubble Chat Global.
     * Sebelumnya ini 2 request terpisah (getChatProfilesByUids +
     * getChatProfileUserNumbers) padahal sama-sama nge-query chat_profiles
     * dengan filter uid yang identik -- digabung biar badge di Chat Global
     * gak nunggu 2 round-trip buat data yang bisa diambil sekali jalan.
     *
     * avatar_url ditambahin di sini karena avatar yang tersimpan di baris
     * PESAN (`chat_messages.avatar_url`) itu SNAPSHOT pas pesan dikirim --
     * kalau user pasang/ganti foto profil SETELAH kirim pesan lama, pesan
     * lama itu tetap nunjukin foto lama (atau kosong) selamanya. Fetch ini
     * dipakai buat NIMPA tampilan avatar di bubble pakai foto TERBARU,
     * konsisten sama yang kelihatan di Top XP / Top Support / dll.
     */
    @GET("rest/v1/chat_profiles")
    suspend fun getChatProfileBadgeDataByUids(
        @Query("firebase_uid") firebaseUidIn: String,
        @Query("select") select: String = "firebase_uid,username_color,user_number,avatar_url"
    ): List<ChatProfile>

    // on_conflict + Prefer=merge-duplicates -> upsert berdasarkan firebase_uid (primary key).
    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/chat_profiles")
    suspend fun upsertChatProfile(
        @Query("on_conflict") onConflict: String = "firebase_uid",
        @Body body: ChatProfileUpsert
    ): List<ChatProfile>
}
