package com.example.ui.screens.chat

import com.example.data.model.ChatMessage

/**
 * Cache in-memory (hidup selama proses app jalan) buat Chat Global, biar
 * layar chat LANGSUNG nampilin pesan + badge terakhir yang udah pernah
 * dimuat, tanpa nunggu spinner. Data terbaru tetap di-fetch di belakang
 * layar dan nimpa cache ini pas udah datang.
 */
object ChatSessionCache {
    @Volatile var messages: List<ChatMessage> = emptyList()
    @Volatile var premiumUids: Set<String> = emptySet()
    @Volatile var clanTagsByUid: Map<String, String> = emptyMap()
    @Volatile var xpLevelsByUid: Map<String, Int> = emptyMap()
    @Volatile var usernameColorsByUid: Map<String, String> = emptyMap()
    @Volatile var userNumbersByUid: Map<String, Long> = emptyMap()
    @Volatile var avatarUrlsByUid: Map<String, String> = emptyMap()
    @Volatile var rolesByUid: Map<String, String> = emptyMap()
    @Volatile var roleBadgeColorsByUid: Map<String, String> = emptyMap()

    fun save(state: ChatUiState) {
        // Jangan timpa cache yang udah isi pakai state kosong (misal pas awal VM).
        if (state.messages.isEmpty()) return
        messages = state.messages
        premiumUids = state.premiumUids
        clanTagsByUid = state.clanTagsByUid
        xpLevelsByUid = state.xpLevelsByUid
        usernameColorsByUid = state.usernameColorsByUid
        userNumbersByUid = state.userNumbersByUid
        avatarUrlsByUid = state.avatarUrlsByUid
        rolesByUid = state.rolesByUid
        roleBadgeColorsByUid = state.roleBadgeColorsByUid
    }
}
