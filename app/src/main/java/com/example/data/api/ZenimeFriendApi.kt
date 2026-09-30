package com.example.data.api

import com.example.data.model.Friendship
import com.example.data.model.FriendshipAcceptBody
import com.example.data.model.FriendshipInsert
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Fitur Add Friend, langsung ke PostgREST (tabel `friendships`), numpang
 * Retrofit [SupabaseNetworkModule]. Lihat backend/supabase/friends_setup.sql.
 */
interface ZenimeFriendApi {

    /** Semua baris yang melibatkan user ini. `or` format: "(requester_uid.eq.X,addressee_uid.eq.X)". */
    @GET("rest/v1/friendships")
    suspend fun getMyFriendships(
        @Query("or") orFilter: String,
        @Query("select") select: String = "id,requester_uid,addressee_uid,status,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 500
    ): List<Friendship>

    /** Hubungan antara 2 user (arah mana pun). `or` format: "(and(...),and(...))". */
    @GET("rest/v1/friendships")
    suspend fun getFriendshipBetween(
        @Query("or") orFilter: String,
        @Query("select") select: String = "id,requester_uid,addressee_uid,status,created_at",
        @Query("limit") limit: Int = 1
    ): List<Friendship>

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/friendships")
    suspend fun sendRequest(@Body body: FriendshipInsert): Response<Void>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/friendships")
    suspend fun accept(
        @Query("id") idEq: String,
        @Body body: FriendshipAcceptBody
    ): Response<Void>

    /** Dipakai buat tolak permintaan, batalin permintaan, dan hapus teman. */
    @DELETE("rest/v1/friendships")
    suspend fun delete(@Query("id") idEq: String): Response<Void>
}
