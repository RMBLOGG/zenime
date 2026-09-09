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
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "reply_to_id") val replyToId: Long? = null,
    @Json(name = "reply_to_username") val replyToUsername: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: String? = null,
    // --- Pesan Suara (VN) -- khusus user Premium yang boleh KIRIM, tapi
    // semua user (premium & free) tetap boleh DENGERIN. "text" = pesan biasa,
    // "voice" = pesan suara. Field `message` tetap keisi teks placeholder
    // ("🎤 Pesan suara") buat pesan voice, biar reply-preview & tempat lain
    // yang masih baca `message.message` gak nampilin kosong.
    @Json(name = "message_type") val messageType: String = "text",
    @Json(name = "audio_url") val audioUrl: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Int? = null,
    // Data amplitudo suara (CSV angka 0-100, mis. "12,40,88,55,...") buat
    // digambar jadi gelombang suara di bubble chat -- lihat WaveformBars.
    @Json(name = "waveform") val waveform: String? = null
)

/** Body buat POST insert pesan baru -- tanpa id/created_at (di-generate DB). */
@JsonClass(generateAdapter = true)
data class ChatMessageInsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "message") val message: String,
    @Json(name = "reply_to_id") val replyToId: Long? = null,
    @Json(name = "reply_to_username") val replyToUsername: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: String? = null,
    @Json(name = "message_type") val messageType: String = "text",
    @Json(name = "audio_url") val audioUrl: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Int? = null,
    @Json(name = "waveform") val waveform: String? = null
)
