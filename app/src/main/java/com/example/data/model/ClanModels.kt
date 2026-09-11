package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Clan(
    @Json(name = "id") val id: String,
    @Json(name = "tag") val tag: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "photo_url") val photoUrl: String? = null,
    @Json(name = "leader_uid") val leaderUid: String,
    @Json(name = "level") val level: Int = 1,
    @Json(name = "total_xp") val totalXp: Long = 0,
    @Json(name = "member_count") val memberCount: Int = 1,
    @Json(name = "member_limit") val memberLimit: Int = 30,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ClanMember(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "role") val role: String, // "leader" | "co_leader" | "member"
    @Json(name = "total_contribution") val totalContribution: Long = 0,
    @Json(name = "joined_at") val joinedAt: String
)

@JsonClass(generateAdapter = true)
data class ClanDonationLogRow(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "amount") val amount: Long,
    @Json(name = "created_at") val createdAt: String
)

/** clan_members digabung sama chat_profiles (username/avatar) buat dirender di list. */
data class ClanMemberDisplay(
    val firebaseUid: String,
    val role: String,
    val totalContribution: Long,
    val joinedAt: String,
    val username: String,
    val avatarUrl: String?
)

/** Baris "Donasi Hari Ini" -- hasil agregasi clan_donation_log per user hari ini. */
data class ClanDonationEntry(
    val firebaseUid: String,
    val username: String,
    val avatarUrl: String?,
    val role: String,
    val amountToday: Long,
    val donationCountToday: Int
)

// --- Body request buat Edge Function (semua butuh header Authorization: Bearer <Firebase ID token>) ---

@JsonClass(generateAdapter = true)
data class CreateClanRequest(
    @Json(name = "name") val name: String,
    @Json(name = "tag") val tag: String,
    @Json(name = "photo_url") val photoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ClanIdRequest(
    @Json(name = "clan_id") val clanId: String
)

@JsonClass(generateAdapter = true)
data class RespondJoinRequestBody(
    @Json(name = "request_id") val requestId: String,
    @Json(name = "approve") val approve: Boolean
)

@JsonClass(generateAdapter = true)
data class DonateToClanRequest(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "amount") val amount: Long
)

@JsonClass(generateAdapter = true)
data class UpdateClanSettingsRequest(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "tag") val tag: String? = null,
    @Json(name = "photo_url") val photoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class KickMemberRequest(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "target_uid") val targetUid: String
)

@JsonClass(generateAdapter = true)
data class ClanActionResponse(
    @Json(name = "clan") val clan: Clan? = null,
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PendingJoinRequestItem(
    @Json(name = "id") val id: String,
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "requested_at") val requestedAt: String
)

@JsonClass(generateAdapter = true)
data class PendingJoinRequestsResponse(
    @Json(name = "requests") val requests: List<PendingJoinRequestItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MyJoinRequestStatusResponse(
    @Json(name = "pending") val pending: Boolean = false
)
