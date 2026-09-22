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

    /** Semua profil user yang tercatat -- dipakai [XpRepository] biar leaderboard nampilin semua user, bukan cuma yang udah punya XP. */
    suspend fun getAllProfiles(): List<ChatProfile> {
        return api.getAllChatProfiles()
    }

    /**
     * Pastiin baris chat_profiles ADA buat user ini -- dipanggil sekali pas
     * login (lihat [com.example.data.repository.AuthRepository]) biar SEMUA
     * user ke-track di leaderboard XP/proxy "daftar semua user", bukan cuma
     * yang kebetulan pernah buka halaman Profil/Chat.
     *
     * SENGAJA cek dulu apa udah ada baris-nya -- kalau udah ada, GAK disentuh
     * sama sekali (biar username/avatar custom yang user set sendiri gak
     * ketimpa tiap kali mereka login ulang / buka app).
     */
    suspend fun ensureProfile(firebaseUid: String, defaultUsername: String, defaultAvatarUrl: String?) {
        val existing = runCatching { getProfile(firebaseUid) }.getOrNull()
        if (existing != null) return
        runCatching { saveProfile(firebaseUid, defaultUsername, defaultAvatarUrl) }
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
        bannerUrl: String? = null,
        usernameColor: String? = null
    ): ChatProfile {
        val result = api.upsertChatProfile(
            body = ChatProfileUpsert(
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl,
                usernameColor = usernameColor
            )
        )
        return result.first()
    }

    /**
     * Warna username custom (hex) buat sekumpulan uid sekaligus -- dipake
     * nge-render warna nama di bubble Chat Global (pola sama kayak
     * [ClanRepository.getClanTagsForUids]/[XpRepository.getLevelsForUids]).
     */
    suspend fun getUsernameColorsForUids(uids: List<String>): Map<String, String> =
        getChatBadgeDataForUids(uids).usernameColors

    /**
     * Warna username + user_number sekaligus dalam satu request (lihat catatan
     * di [com.example.data.api.ZenimeSupabaseApi.getChatProfileBadgeDataByUids]).
     * Dipakai berbarengan dari [ChatViewModel] biar 2 badge itu keisi dari
     * SATU fetch, bukan 2 fetch yang nunggu bergantian.
     */
    suspend fun getChatBadgeDataForUids(uids: List<String>): ChatBadgeData {
        val distinctUids = uids.filter { it.isNotBlank() }.distinct()
        if (distinctUids.isEmpty()) return ChatBadgeData(emptyMap(), emptyMap(), emptyMap())

        val filter = "in.(${distinctUids.joinToString(",")})"
        val rows = api.getChatProfileBadgeDataByUids(firebaseUidIn = filter)
        return ChatBadgeData(
            usernameColors = rows.mapNotNull { p -> p.usernameColor?.let { p.firebaseUid to it } }.toMap(),
            userNumbers = rows.mapNotNull { p -> p.userNumber?.let { p.firebaseUid to it } }.toMap(),
            // Foto profil TERKINI, buat nimpa avatar_url lama yang ke-nempel
            // di baris pesan (lihat catatan di ZenimeSupabaseApi.getChatProfileBadgeDataByUids).
            avatarUrls = rows.mapNotNull { p -> p.avatarUrl?.let { p.firebaseUid to it } }.toMap()
        )
    }
}

/** Hasil [ChatRepository.getChatBadgeDataForUids] -- warna username + user_number + avatar_url terkini per uid. */
data class ChatBadgeData(
    val usernameColors: Map<String, String>,
    val userNumbers: Map<String, Long>,
    val avatarUrls: Map<String, String> = emptyMap()
)
