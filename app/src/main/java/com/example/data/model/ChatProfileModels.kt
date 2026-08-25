package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Baris di tabel `chat_profiles` -- override username/avatar buat Chat Global. */
@JsonClass(generateAdapter = true)
data class ChatProfile(
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

/** Body buat upsert profil (insert kalau belum ada, update kalau udah ada). */
@JsonClass(generateAdapter = true)
data class ChatProfileUpsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?
)
