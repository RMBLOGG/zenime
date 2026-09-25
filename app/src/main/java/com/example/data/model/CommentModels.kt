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
    @Json(name = "is_pinned") val isPinned: Boolean = false,

    // --- Snapshot anime/episode, khusus dipake tab "Komentar" di halaman
    // Profil (biar bisa nampilin thumbnail+judul tanpa query balik ke
    // tabel anime tiap baris) -- kosong/null aman diabaikan di sheet
    // komentar per-episode yang gak butuh field ini.
    @Json(name = "anime_title") val animeTitle: String? = null,
    @Json(name = "anime_poster_url") val animePosterUrl: String? = null,
    @Json(name = "episode_index") val episodeIndex: String? = null,
    // Jumlah balasan komentar ini -- di-update otomatis lewat trigger DB,
    // BUKAN dihitung di app (lihat SETUP_KOMENTAR_PROFIL.sql). Cuma valid
    // buat komentar top-level (parentId null); balasan selalu 0.
    @Json(name = "reply_count") val replyCount: Int = 0
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
    @Json(name = "is_pinned") val isPinned: Boolean = false,
    @Json(name = "anime_title") val animeTitle: String? = null,
    @Json(name = "anime_poster_url") val animePosterUrl: String? = null,
    @Json(name = "episode_index") val episodeIndex: String? = null
)
