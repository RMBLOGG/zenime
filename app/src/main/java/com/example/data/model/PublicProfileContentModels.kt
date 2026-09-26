package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Body upsert satu baris ke `user_favorites` -- salinan server dari [com.example.data.local.FavoriteEntity]. */
@JsonClass(generateAdapter = true)
data class FavoriteUpsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "anime_id") val animeId: String,
    @Json(name = "title") val title: String,
    @Json(name = "poster_url") val posterUrl: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "status") val status: String?
)

/** Body upsert satu baris ke `user_watch_history` -- salinan server dari [com.example.data.local.WatchHistoryEntity]. */
@JsonClass(generateAdapter = true)
data class WatchHistoryUpsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "anime_id") val animeId: String,
    @Json(name = "anime_title") val animeTitle: String,
    @Json(name = "poster_url") val posterUrl: String?,
    @Json(name = "episode_id") val episodeId: String,
    @Json(name = "episode_title") val episodeTitle: String?,
    @Json(name = "episode_index") val episodeIndex: String?,
    @Json(name = "progress_ms") val progressMs: Long,
    @Json(name = "duration_ms") val durationMs: Long
)

/** Satu baris favorit publik punya user lain (hasil GET langsung, RLS-gated). */
@JsonClass(generateAdapter = true)
data class PublicFavoriteRow(
    @Json(name = "anime_id") val animeId: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "status") val status: String? = null
)

/** Satu baris riwayat tontonan publik punya user lain (hasil GET langsung, RLS-gated). */
@JsonClass(generateAdapter = true)
data class PublicWatchHistoryRow(
    @Json(name = "anime_id") val animeId: String = "",
    @Json(name = "anime_title") val animeTitle: String = "",
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "episode_id") val episodeId: String = "",
    @Json(name = "episode_title") val episodeTitle: String? = null,
    @Json(name = "episode_index") val episodeIndex: String? = null,
    @Json(name = "progress_ms") val progressMs: Long = 0L,
    @Json(name = "duration_ms") val durationMs: Long = 0L
)

/**
 * Hasil gabungan [PublicProfileRepository.getPublicContent] (toggle privasi + favorit/riwayat).
 *
 * `favorites`/`history` == null berarti PRIVAT (toggle user itu mati) --
 * beda sama list kosong, yang berarti publik tapi memang belum ada isinya.
 * Bedain dua kondisi ini di UI (lihat ProfileViewModel/ProfileScreen).
 */
@JsonClass(generateAdapter = true)
data class PublicProfileContentResponse(
    @Json(name = "favorites_public") val favoritesPublic: Boolean = false,
    @Json(name = "history_public") val historyPublic: Boolean = false,
    @Json(name = "favorites") val favorites: List<PublicFavoriteRow>? = null,
    @Json(name = "history") val history: List<PublicWatchHistoryRow>? = null
)
