package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Satu baris pesan di tabel `global_chat_messages` (Supabase Postgres).
 * Dipakai buat parsing response GET maupun POST (?select=...) lewat
 * PostgREST, jadi field-nya sengaja dibikin nullable/default biar aman
 * kalau ada kolom yang belum keisi.
 */
@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "username") val username: String = "Pengguna",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "message") val message: String = "",
    @Json(name = "created_at") val createdAt: String = ""
)

/** Body buat POST insert pesan baru -- tanpa id/created_at (di-generate DB). */
@JsonClass(generateAdapter = true)
data class ChatMessageInsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "message") val message: String
)
