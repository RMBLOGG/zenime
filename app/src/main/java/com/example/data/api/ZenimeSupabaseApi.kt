package com.example.data.api

import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageInsert
import com.example.data.model.ChatProfile
import com.example.data.model.ChatProfileUpsert
import com.example.data.model.PremiumPackagesResponse
import com.example.data.model.PremiumStatusResponse
import com.example.data.model.ZenimeCodeResponse
import retrofit2.http.Body
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

    // --- Chat Global ---
    // Dua endpoint di bawah manggil langsung tabel `global_chat_messages`
    // lewat PostgREST bawaan Supabase (bukan Edge Function), jadi cukup
    // tabel + RLS policy-nya dibikin di dashboard (lihat catatan setup).

    @GET("rest/v1/global_chat_messages")
    suspend fun getChatMessages(
        @Query("select") select: String = "id,firebase_uid,username,avatar_url,message,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<ChatMessage>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/global_chat_messages")
    suspend fun postChatMessage(@Body body: ChatMessageInsert): List<ChatMessage>

    // --- Profil Chat (username & avatar custom) ---

    @GET("rest/v1/chat_profiles")
    suspend fun getChatProfile(
        @Query("firebase_uid") firebaseUidEq: String,
        @Query("select") select: String = "firebase_uid,username,avatar_url,updated_at",
        @Query("limit") limit: Int = 1
    ): List<ChatProfile>

    // on_conflict + Prefer=merge-duplicates -> upsert berdasarkan firebase_uid (primary key).
    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/chat_profiles")
    suspend fun upsertChatProfile(
        @Query("on_conflict") onConflict: String = "firebase_uid",
        @Body body: ChatProfileUpsert
    ): List<ChatProfile>
}
