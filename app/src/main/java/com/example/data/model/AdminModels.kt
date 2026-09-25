package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Role yang dikenal sistem. User biasa direpresentasiin sebagai role null (bukan enum). */
enum class ZenimeRole(val value: String) {
    DEVELOPER("developer"),
    ADMIN("admin"),
    MODERATOR("moderator");

    companion object {
        fun fromValue(value: String?): ZenimeRole? = entries.find { it.value == value }
    }
}

/** Warna badge default per role -- dipakai kalau user gak punya badge_color custom. */
fun ZenimeRole.defaultBadgeColorHex(): String = when (this) {
    ZenimeRole.DEVELOPER -> "#E53935" // merah
    ZenimeRole.ADMIN -> "#43A047"     // hijau
    ZenimeRole.MODERATOR -> "#8E24AA" // ungu
}

/** Response zenime-admin-get-role. */
@JsonClass(generateAdapter = true)
data class RoleInfoResponse(
    @Json(name = "role") val role: String? = null,
    @Json(name = "badge_color") val badgeColor: String? = null
)

/** Satu baris di zenime-admin-list-roles. */
@JsonClass(generateAdapter = true)
data class RoleListEntry(
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "role") val role: String = "",
    @Json(name = "badge_color") val badgeColor: String? = null,
    @Json(name = "assigned_by") val assignedBy: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "username") val username: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class RoleListResponse(
    @Json(name = "roles") val roles: List<RoleListEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SetRoleRequest(
    @Json(name = "target_uid") val targetUid: String,
    // Dipakai kalau remove=false. Sengaja gak dibikin nullable buat cabut
    // role -- pakai flag `remove` di bawah, biar gak gantung ke perilaku
    // serialize-null Moshi (yang default-nya SKIP field null, bukan kirim
    // "role":null).
    @Json(name = "role") val role: String = "",
    @Json(name = "remove") val remove: Boolean = false,
    @Json(name = "badge_color") val badgeColor: String? = null
)

@JsonClass(generateAdapter = true)
data class TargetUidRequest(
    @Json(name = "target_uid") val targetUid: String,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceIdRequest(
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class MessageIdRequest(
    @Json(name = "message_id") val messageId: Long
)

@JsonClass(generateAdapter = true)
data class AdminActionResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "error") val error: String? = null,
    @Json(name = "device_id") val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class CheckBanResponse(
    @Json(name = "banned") val banned: Boolean = false,
    @Json(name = "scope") val scope: String? = null, // "account" | "device"
    @Json(name = "reason") val reason: String? = null
)
