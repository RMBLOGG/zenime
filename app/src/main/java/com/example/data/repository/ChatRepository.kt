package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageInsert

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
}
