package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Baris di tabel `chat_profiles` -- override username/avatar buat Chat
 * Global, SEKALIGUS dipakai sebagai data profil akun Zenime secara umum
 * (dipakai juga di ProfileScreen). `bannerUrl` sama-sama upload foto asli
 * kayak avatar, dan sama-sama dibatasi khusus member Premium (lihat
 * BannerUploader & ProfileViewModel.uploadBanner).
 */
@JsonClass(generateAdapter = true)
data class ChatProfile(
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

/** Body buat upsert profil (insert kalau belum ada, update kalau udah ada). */
@JsonClass(generateAdapter = true)
data class ChatProfileUpsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "banner_url") val bannerUrl: String? = null
)
