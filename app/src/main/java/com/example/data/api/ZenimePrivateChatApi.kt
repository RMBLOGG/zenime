package com.example.data.api

import com.example.data.model.PrivateMessage
import com.example.data.model.PrivateMessageInsert
import com.example.data.model.PrivateMessageReadBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Chat teman (DM) langsung ke PostgREST, tabel `private_messages`. */
interface ZenimePrivateChatApi {

    /** Pesan antara 2 user. `or` format: "(and(sender_uid.eq.A,recipient_uid.eq.B),and(sender_uid.eq.B,recipient_uid.eq.A))". */
    @GET("rest/v1/private_messages")
    suspend fun getConversation(
        @Query("or") orFilter: String,
        @Query("select") select: String = "id,sender_uid,recipient_uid,message,created_at,read_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100
    ): List<PrivateMessage>

    /** Pesan terbaru yang melibatkan user ini -- dipakai buat nyusun daftar percakapan. */
    @GET("rest/v1/private_messages")
    suspend fun getRecent(
        @Query("or") orFilter: String,
        @Query("select") select: String = "id,sender_uid,recipient_uid,message,created_at,read_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 300
    ): List<PrivateMessage>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/private_messages")
    suspend fun send(@Body body: PrivateMessageInsert): List<PrivateMessage>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/private_messages")
    suspend fun markRead(
        @Query("recipient_uid") recipientEq: String,
        @Query("sender_uid") senderEq: String,
        @Query("read_at") readAtIs: String = "is.null",
        @Body body: PrivateMessageReadBody
    ): Response<Void>
}
