package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageInsert
import com.example.data.model.ChatProfile
import com.example.data.model.ChatProfileUpsert

/**
 * Repository buat fitur Chat Global -- baca & kirim pesan lewat tabel
 * `global_chat_messages` di Supabase (PostgREST langsung, bukan Edge
 * Function, biar simpel karena gak butuh logic khusus di server).
 */
class ChatRepository(
    private val api: com.example.data.api.ZenimeSupabaseApi = SupabaseNetworkModule.api
) {
    /** Ambil pesan terbaru (DESC dari server), balikin dalam urutan kronologis (lama -> baru). */
    suspend fun getMessages(limit: Int = 50): List<ChatMessage> {
        return api.getChatMessages(limit = limit).reversed()
    }

    suspend fun sendMessage(
        firebaseUid: String,
        username: String,
        avatarUrl: String?,
        message: String,
        replyToId: Long? = null,
        replyToUsername: String? = null,
        replyToMessage: String? = null
    ): ChatMessage {
        val result = api.postChatMessage(
            ChatMessageInsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                message = message,
                replyToId = replyToId,
                replyToUsername = replyToUsername,
                replyToMessage = replyToMessage
            )
        )
        return result.first()
    }

    /**
     * Kirim pesan suara (VN) -- pemanggil (ChatViewModel) wajib udah ngecek
     * status Premium sebelum manggil ini, karena kirim VN dibatasi khusus
     * Premium (dengerin/play tetap terbuka buat semua user).
     */
    suspend fun sendVoiceMessage(
        firebaseUid: String,
        username: String,
        avatarUrl: String?,
        audioUrl: String,
        durationSeconds: Int,
        waveform: String?,
        replyToId: Long? = null,
        replyToUsername: String? = null,
        replyToMessage: String? = null
    ): ChatMessage {
        val result = api.postChatMessage(
            ChatMessageInsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                message = "🎤 Pesan suara",
                messageType = "voice",
                audioUrl = audioUrl,
                durationSeconds = durationSeconds,
                waveform = waveform,
                replyToId = replyToId,
                replyToUsername = replyToUsername,
                replyToMessage = replyToMessage
            )
        )
        return result.first()
    }

    /**
     * Hapus pesan milik sendiri. Filter firebase_uid ikut dikirim di query
     * (bukan cuma dicek di UI) biar request-nya sendiri gak bisa dipakai
     * buat hapus pesan orang lain.
     */
    suspend fun deleteMessage(id: Long, firebaseUid: String) {
        val response = api.deleteChatMessage(
            idEq = "eq.$id",
            firebaseUidEq = "eq.$firebaseUid"
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal menghapus pesan (${response.code()})")
        }
    }

    /** Ambil profil chat custom (username/avatar override) user, kalau ada. */
    suspend fun getProfile(firebaseUid: String): ChatProfile? {
        return api.getChatProfile(firebaseUidEq = "eq.$firebaseUid").firstOrNull()
    }

    /**
     * Simpan/update username, avatar, & banner custom user (upsert berdasarkan
     * firebase_uid). PENTING: upsert ini nge-replace SEMUA kolom yang dikirim,
     * jadi pemanggil WAJIB selalu ikut kirim nilai field yang gak diubah
     * (bukan cuma field yang barusan diedit) -- kalau enggak, field itu bakal
     * ketimpa null. Contoh: ganti username doang tetap harus kirim avatarUrl
     * & bannerUrl yang lagi aktif sekarang, bukan null.
     */
    suspend fun saveProfile(
        firebaseUid: String,
        username: String,
        avatarUrl: String?,
        bannerUrl: String? = null
    ): ChatProfile {
        val result = api.upsertChatProfile(
            body = ChatProfileUpsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl
            )
        )
        return result.first()
    }
}
