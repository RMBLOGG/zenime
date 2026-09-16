package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Baris tabel user_xp -- dipakai buat halaman profil sendiri & leaderboard. */
@JsonClass(generateAdapter = true)
data class UserXp(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "total_xp") val totalXp: Long = 0,
    @Json(name = "level") val level: Int = 1,
    @Json(name = "updated_at") val updatedAt: String? = null
)

/** user_xp digabung sama chat_profiles (username/avatar) buat dirender di leaderboard. */
data class UserXpDisplay(
    val firebaseUid: String,
    val totalXp: Long,
    val level: Int,
    val username: String,
    val avatarUrl: String?
)

/** Body ke Edge Function watch-xp-heartbeat. firebase_uid TIDAK dikirim di sini
 * dengan sengaja -- server ambil uid dari Firebase ID Token yang diverifikasi,
 * bukan dari body (lihat catatan di [com.example.data.repository.XpRepository]). */
@JsonClass(generateAdapter = true)
data class WatchXpHeartbeatRequest(
    @Json(name = "minutes") val minutes: Int
)

@JsonClass(generateAdapter = true)
data class WatchXpHeartbeatResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "error") val error: String? = null
)
