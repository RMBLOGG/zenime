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
    // Saldo ZCoin hasil donasi yang bisa dibelanjakan (beli kuota member).
    // Terpisah dari total_xp (total_xp cuma buat level, gak pernah berkurang).
    @Json(name = "treasury_balance") val treasuryBalance: Long = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ClanMember(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "role") val role: String, // "leader" | "vice_leader" | "admiral" | "co_leader" (Officer) | "member"
    @Json(name = "total_contribution") val totalContribution: Long = 0,
    @Json(name = "joined_at") val joinedAt: String
)

/**
 * Hierarki role clan (tinggi -> rendah):
 * Leader > Vice Leader > Admiral > Officer > Member.
 *
 * Officer disimpen di DB sebagai "co_leader" (nilai lama, tetap dipakai biar
 * data yang udah ada gak rusak).
 *
 * Semua aturan izin dikumpulin di sini biar gampang diubah dari satu tempat.
 * Ini CUMA buat nampilin/nyembunyiin tombol di UI -- validasi aslinya WAJIB
 * tetep dicek ulang di Edge Function (jangan percaya client).
 */
object ClanRoles {
    const val LEADER = "leader"
    const val VICE_LEADER = "vice_leader"
    const val ADMIRAL = "admiral"
    const val OFFICER = "co_leader"
    const val MEMBER = "member"

    fun rank(role: String?): Int = when (role) {
        LEADER -> 4
        VICE_LEADER -> 3
        ADMIRAL -> 2
        OFFICER -> 1
        else -> 0
    }

    fun label(role: String?): String = when (role) {
        LEADER -> "LEADER"
        VICE_LEADER -> "VICE LEADER"
        ADMIRAL -> "ADMIRAL"
        OFFICER -> "OFFICER"
        else -> "MEMBER"
    }

    /** Officer ke atas boleh buka Kelola Clan (terima/tolak request join). */
    fun canManageClan(role: String?): Boolean = rank(role) >= 1

    /** Vice Leader & Admiral (dan Leader) boleh kick target yang pangkatnya lebih rendah. */
    fun canKick(actorRole: String?, targetRole: String?): Boolean =
        rank(actorRole) >= 2 && rank(targetRole) < rank(actorRole)

    /**
     * Role yang boleh dikasih [actorRole] ke target dengan role [targetRole].
     * Aturannya: cuma boleh ngubah target yang pangkatnya di bawah actor, dan
     * cuma boleh ngasih role yang pangkatnya di bawah actor juga.
     *  - Leader      -> Vice Leader / Admiral / Officer / Member
     *  - Vice Leader -> Admiral / Officer / Member
     *  - Admiral     -> Officer / Member
     */
    fun assignableRoles(actorRole: String?, targetRole: String?): List<String> {
        val actorRank = rank(actorRole)
        if (actorRank < 2 || rank(targetRole) >= actorRank) return emptyList()
        return listOf(VICE_LEADER, ADMIRAL, OFFICER, MEMBER)
            .filter { rank(it) < actorRank && it != targetRole }
    }

    fun canActOn(actorRole: String?, targetRole: String?): Boolean =
        canKick(actorRole, targetRole) || assignableRoles(actorRole, targetRole).isNotEmpty()
}

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
    val avatarUrl: String?,
    // ID urut user (#ID) -- sama kayak yang tampil di Chat Global.
    val userNumber: Long? = null
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

/** Ubah role 1 member. role: salah satu dari [ClanRoles] (vice_leader / admiral / co_leader / member). */
@JsonClass(generateAdapter = true)
data class SetMemberRoleRequest(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "target_uid") val targetUid: String,
    @Json(name = "role") val role: String
)

/** Body request beli kuota member. 1 paket = [ClanSlotShop.SLOTS_PER_PACK] kuota. */
@JsonClass(generateAdapter = true)
data class BuyMemberSlotsRequest(
    @Json(name = "clan_id") val clanId: String,
    @Json(name = "packs") val packs: Int
)

@JsonClass(generateAdapter = true)
data class BuyMemberSlotsResponse(
    @Json(name = "member_limit") val memberLimit: Int? = null,
    @Json(name = "treasury_balance") val treasuryBalance: Long? = null,
    @Json(name = "error") val error: String? = null
)

/**
 * Harga & batas beli kuota member. Angka ini CUMA buat tampilan UI --
 * yang berlaku tetap konstanta di RPC buy_member_slots (SQL). Kalau mau
 * ubah harga/batas, ubah DI DUA TEMPAT biar tampilan gak beda sama server.
 */
object ClanSlotShop {
    const val SLOTS_PER_PACK = 5
    const val PRICE_PER_PACK = 5_000L
    const val MAX_MEMBER_LIMIT = 150

    /** Berapa paket maksimal yang masih muat sampai batas [MAX_MEMBER_LIMIT]. */
    fun maxPacksFor(memberLimit: Int): Int =
        ((MAX_MEMBER_LIMIT - memberLimit) / SLOTS_PER_PACK).coerceAtLeast(0)
}

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

/** PendingJoinRequestItem digabung sama chat_profiles, dipakai di layar Kelola Clan. */
data class PendingJoinRequestDisplay(
    val requestId: String,
    val firebaseUid: String,
    val username: String,
    val avatarUrl: String?,
    val requestedAt: String
)

// --- Buat badge tag clan di Chat Global: manfaatin PostgREST embedding
// lewat FK clan_members.clan_id -> clans.id, jadi satu request langsung
// dapet tag-nya tanpa perlu query clans terpisah. ---

@JsonClass(generateAdapter = true)
data class ClanTagOnly(
    @Json(name = "tag") val tag: String
)

@JsonClass(generateAdapter = true)
data class ClanTagLookupRow(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "clans") val clan: ClanTagOnly? = null
)
