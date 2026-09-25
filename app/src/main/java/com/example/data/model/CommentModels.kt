package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Satu baris komentar di tabel `episode_comments` (Supabase Postgres).
 * Dipakai buat komentar TOP-LEVEL maupun BALASAN (reply) -- dibedain lewat
 * [parentId]: null = komentar top-level, keisi = balasan yang nempel di
 * thread komentar dengan id itu (thread cuma 1 level, gak nested lagi
 * kayak reply-dari-reply, sama kayak referensi "Threads" bottom sheet).
 */
@JsonClass(generateAdapter = true)
data class EpisodeComment(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "episode_id") val episodeId: String = "",
    @Json(name = "anime_id") val animeId: String = "",
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "username") val username: String = "Pengguna",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "comment") val comment: String = "",
    @Json(name = "parent_id") val parentId: Long? = null,
    // Username tujuan balasan -- cuma keisi kalau reply ini bales BALASAN
    // lain di dalam thread yang sama (bukan bales komentar utamanya
    // langsung), biar kelihatan "@username" di depan teks balasan.
    @Json(name = "reply_to_username") val replyToUsername: String? = null,
    @Json(name = "created_at") val createdAt: String = "",
    // Komentar yang dikirim user Premium lewat tombol mahkota -- disorot
    // beda (border emas) & diprioritaskan di tab "Top Comment".
    @Json(name = "is_pinned") val isPinned: Boolean = false
)

/** Body buat POST insert komentar/balasan baru -- id & created_at di-generate DB. */
@JsonClass(generateAdapter = true)
data class EpisodeCommentInsert(
    @Json(name = "episode_id") val episodeId: String,
    @Json(name = "anime_id") val animeId: String,
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "comment") val comment: String,
    @Json(name = "parent_id") val parentId: Long? = null,
    @Json(name = "reply_to_username") val replyToUsername: String? = null,
    @Json(name = "is_pinned") val isPinned: Boolean = false
)
