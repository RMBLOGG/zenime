package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimePrivateChatApi
import com.example.data.model.PrivateMessage
import com.example.data.model.PrivateMessageInsert
import com.example.data.model.PrivateMessageReadBody
import java.time.Instant

/** Repository chat teman (DM). Semua lewat PostgREST, lihat private_chat_setup.sql. */
class PrivateChatRepository(
    private val api: ZenimePrivateChatApi = SupabaseNetworkModule.privateChatApi
) {

    /** Urut lama -> baru (siap dirender dari atas ke bawah). */
    suspend fun getConversation(myUid: String, otherUid: String): Result<List<PrivateMessage>> = runCatching {
        val filter = "(and(sender_uid.eq.$myUid,recipient_uid.eq.$otherUid)," +
            "and(sender_uid.eq.$otherUid,recipient_uid.eq.$myUid))"
        api.getConversation(orFilter = filter).sortedBy { it.id }
    }

    suspend fun getRecent(myUid: String): Result<List<PrivateMessage>> = runCatching {
        api.getRecent(orFilter = "(sender_uid.eq.$myUid,recipient_uid.eq.$myUid)")
    }

    suspend fun send(
        myUid: String,
        otherUid: String,
        text: String,
        replyToId: Long? = null,
        replyToSenderUid: String? = null,
        replyToMessage: String? = null
    ): Result<PrivateMessage> = runCatching {
        api.send(
            PrivateMessageInsert(
                senderUid = myUid,
                recipientUid = otherUid,
                message = text,
                replyToId = replyToId,
                replyToSenderUid = replyToSenderUid,
                replyToMessage = replyToMessage
            )
        ).first()
    }

    suspend fun markRead(myUid: String, otherUid: String): Result<Unit> = runCatching {
        val response = api.markRead(
            recipientEq = "eq.$myUid",
            senderEq = "eq.$otherUid",
            body = PrivateMessageReadBody(readAt = Instant.now().toString())
        )
        if (!response.isSuccessful) throw IllegalStateException("markRead gagal (HTTP ${response.code()})")
    }
}
