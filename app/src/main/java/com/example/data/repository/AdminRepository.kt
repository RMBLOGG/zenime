package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeAdminApi
import com.example.data.model.CheckBanResponse
import com.example.data.model.DeviceIdRequest
import com.example.data.model.MessageIdRequest
import com.example.data.model.RoleInfoResponse
import com.example.data.model.RoleListEntry
import com.example.data.model.SetRoleRequest
import com.example.data.model.TargetUidRequest
import com.example.data.model.UserListEntry
import com.example.data.model.ZenimeRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Repository fitur Admin Panel (role developer/admin/moderator, ban
 * device/akun, hapus pesan chat). Semua aksi lewat Edge Function dengan
 * Firebase ID Token asli di header Authorization -- pola sama kayak
 * [ClanRepository], role-nya dicek ULANG di server (bukan trust dari app),
 * lihat SQL + Edge Function di /supabase.
 */
class AdminRepository(
    private val api: ZenimeAdminApi = SupabaseNetworkModule.adminApi
) {

    private suspend fun authHeader(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Kamu harus login dulu")
        val token = user.getIdToken(false).await()?.token
            ?: throw IllegalStateException("Gagal ambil Firebase ID Token, coba login ulang")
        return "Bearer $token"
    }

    /**
     * Role beberapa uid sekaligus, dipakai buat nge-render badge role
     * (centang berwarna) di Chat Global -- lewat PostgREST langsung (public
     * SELECT), gak butuh Firebase token per-user kayak [getMyRole]. Uid yang
     * gak punya role gak bakal muncul di map hasilnya.
     */
    suspend fun getRolesForUids(firebaseUids: List<String>): Map<String, RoleListEntry> {
        val distinctUids = firebaseUids.filter { it.isNotBlank() }.distinct()
        if (distinctUids.isEmpty()) return emptyMap()
        return try {
            val filter = "in.(${distinctUids.joinToString(",")})"
            api.getRolesForUids(firebaseUidIn = filter).associateBy { it.firebaseUid }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Role user yang lagi login (null = user biasa). Dipakai buat gating UI. */
    suspend fun getMyRole(): Result<RoleInfoResponse> = try {
        Result.success(api.getMyRole(authHeader()))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Daftar semua pemegang role -- khusus developer, ditolak server kalau bukan. */
    suspend fun listRoles(): Result<List<RoleListEntry>> = try {
        Result.success(api.listRoles(authHeader()).roles)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Daftar/cari SEMUA user (bukan cuma pemegang role) -- sumber data tab
     * "Semua User", biar developer/admin/moderator tinggal cari nama/kode
     * lalu pencet tombol aksi di barisnya, gak perlu ketik UID manual lagi.
     * Boleh dipanggil siapapun yang punya role (dicek ulang di server).
     */
    suspend fun listUsers(search: String? = null, offset: Int = 0): Result<Pair<List<UserListEntry>, Boolean>> = try {
        val response = api.listUsers(authHeader(), search?.takeIf { it.isNotBlank() }, offset = offset)
        Result.success(response.users to response.hasMore)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Kasih/ganti role + opsional custom warna badge. Khusus developer. */
    suspend fun setRole(targetUid: String, role: ZenimeRole, badgeColor: String? = null): Result<Unit> = try {
        val response = api.setRole(
            authHeader(),
            SetRoleRequest(targetUid = targetUid, role = role.value, remove = false, badgeColor = badgeColor)
        )
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal set role"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Cabut role user (balik jadi user biasa). Khusus developer. */
    suspend fun removeRole(targetUid: String): Result<Unit> = try {
        val response = api.setRole(authHeader(), SetRoleRequest(targetUid = targetUid, remove = true))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal cabut role"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Ban akun (moderator/developer). Server nolak kalau target punya role. */
    suspend fun banUser(targetUid: String, reason: String? = null): Result<Unit> = try {
        val response = api.banUser(authHeader(), TargetUidRequest(targetUid, reason))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal ban user"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun unbanUser(targetUid: String): Result<Unit> = try {
        val response = api.unbanUser(authHeader(), TargetUidRequest(targetUid))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal cabut ban"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Ban device dari uid target (khusus developer). Server nolak kalau target punya role. */
    suspend fun banDevice(targetUid: String, reason: String? = null): Result<Unit> = try {
        val response = api.banDevice(authHeader(), TargetUidRequest(targetUid, reason))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal ban device"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun unbanDevice(deviceId: String): Result<Unit> = try {
        val response = api.unbanDevice(authHeader(), DeviceIdRequest(deviceId))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal cabut ban device"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Hapus pesan chat orang lain (admin/developer). */
    suspend fun deleteMessage(messageId: Long): Result<Unit> = try {
        val response = api.deleteMessage(authHeader(), MessageIdRequest(messageId))
        if (response.success) Result.success(Unit) else Result.failure(IllegalStateException(response.error ?: "Gagal hapus pesan"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Dipanggil SEKALI abis Firebase login sukses (LoginViewModel), sebelum
     * masuk Home. Kalau banned=true, caller WAJIB sign-out dari FirebaseAuth
     * & nampilin `reason` ke user -- repository ini sengaja gak sign-out
     * sendiri, biar keputusan UI (dialog, navigasi balik ke Login) tetap di
     * layer ViewModel/Screen.
     */
    suspend fun checkBan(deviceId: String): Result<CheckBanResponse> = try {
        Result.success(api.checkBan(authHeader(), DeviceIdRequest(deviceId)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
