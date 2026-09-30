package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Baris tabel `private_messages` (lihat backend/supabase/private_chat_setup.sql). */
@JsonClass(generateAdapter = true)
data class PrivateMessage(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "sender_uid") val senderUid: String = "",
    @Json(name = "recipient_uid") val recipientUid: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "read_at") val readAt: String? = null,
    // Reply: snapshot id + pengirim + isi pesan yang dibalas (null = bukan reply).
    @Json(name = "reply_to_id") val replyToId: Long? = null,
    @Json(name = "reply_to_sender_uid") val replyToSenderUid: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class PrivateMessageInsert(
    @Json(name = "sender_uid") val senderUid: String,
    @Json(name = "recipient_uid") val recipientUid: String,
    @Json(name = "message") val message: String,
    @Json(name = "reply_to_id") val replyToId: Long? = null,
    @Json(name = "reply_to_sender_uid") val replyToSenderUid: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class PrivateMessageReadBody(
    @Json(name = "read_at") val readAt: String
)
