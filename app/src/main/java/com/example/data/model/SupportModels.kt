package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Satu baris di daftar Top Support (hasil Edge Function `zenime-top-supporters`).
 * Kalau [isLinked] true, [name]/[avatarUrl]/[usernameColor] diambil dari akun
 * Zenime donatur; kalau false, [name] cuma nama yang diketik di SociaBuzz.
 */
@JsonClass(generateAdapter = true)
data class TopSupporter(
    @Json(name = "rank") val rank: Int = 0,
    @Json(name = "name") val name: String = "Anonim",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "username_color") val usernameColor: String? = null,
    @Json(name = "total_amount") val totalAmount: Long = 0,
    @Json(name = "donation_count") val donationCount: Int = 0,
    @Json(name = "is_linked") val isLinked: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TopSupportersResponse(
    @Json(name = "supporters") val supporters: List<TopSupporter> = emptyList(),
    @Json(name = "message") val message: String? = null
)
