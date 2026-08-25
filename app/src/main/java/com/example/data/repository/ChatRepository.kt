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
        message: String
    ): ChatMessage {
        val result = api.postChatMessage(
            ChatMessageInsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                message = message
            )
        )
        return result.first()
    }

    /** Ambil profil chat custom (username/avatar override) user, kalau ada. */
    suspend fun getProfile(firebaseUid: String): ChatProfile? {
        return api.getChatProfile(firebaseUidEq = "eq.$firebaseUid").firstOrNull()
    }

    /** Simpan/update username & avatar custom user (upsert berdasarkan firebase_uid). */
    suspend fun saveProfile(firebaseUid: String, username: String, avatarUrl: String?): ChatProfile {
        val result = api.upsertChatProfile(
            body = ChatProfileUpsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl
            )
        )
        return result.first()
    }
}
