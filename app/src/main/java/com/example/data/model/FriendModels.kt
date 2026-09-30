package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object FriendStatus {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
}

/** Baris tabel `friendships` (lihat backend/supabase/friends_setup.sql). */
@JsonClass(generateAdapter = true)
data class Friendship(
    @Json(name = "id") val id: String = "",
    @Json(name = "requester_uid") val requesterUid: String = "",
    @Json(name = "addressee_uid") val addresseeUid: String = "",
    @Json(name = "status") val status: String = FriendStatus.PENDING,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class FriendshipInsert(
    @Json(name = "requester_uid") val requesterUid: String,
    @Json(name = "addressee_uid") val addresseeUid: String,
    @Json(name = "status") val status: String = FriendStatus.PENDING
)

@JsonClass(generateAdapter = true)
data class FriendshipAcceptBody(
    @Json(name = "status") val status: String = FriendStatus.ACCEPTED,
    @Json(name = "responded_at") val respondedAt: String
)

/** Hubungan user yang lagi login terhadap user yang profilnya dilihat. */
sealed interface FriendRelation {
    data object None : FriendRelation
    /** Aku yang kirim permintaan, nunggu dia terima. */
    data class OutgoingPending(val friendshipId: String) : FriendRelation
    /** Dia yang kirim permintaan ke aku, nunggu aku terima/tolak. */
    data class IncomingPending(val friendshipId: String) : FriendRelation
    data class Friends(val friendshipId: String) : FriendRelation
}

/** Item di layar Teman: friendship digabung sama username/avatar lawan. */
data class FriendDisplay(
    val friendshipId: String,
    val firebaseUid: String,
    val username: String,
    val avatarUrl: String?
)
