package com.example.data.api

import com.example.data.model.AdminActionResponse
import com.example.data.model.CheckBanResponse
import com.example.data.model.DeviceIdRequest
import com.example.data.model.MessageIdRequest
import com.example.data.model.RoleInfoResponse
import com.example.data.model.RoleListResponse
import com.example.data.model.SetRoleRequest
import com.example.data.model.TargetUidRequest
import com.example.data.model.RoleListEntry
import com.example.data.model.UserListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit khusus fitur Admin Panel (role, ban device/akun, hapus
 * pesan chat). Numpang base URL & OkHttpClient dari [SupabaseNetworkModule]
 * (lihat `adminApi` di situ), sama pola kayak [ZenimeClanApi].
 *
 * Semua endpoint di sini manggil Edge Function -- header Authorization WAJIB
 * isinya Firebase ID TOKEN ASLI (bukan anon key), lihat
 * [com.example.data.repository.AdminRepository.authHeader]. Anon key tetap
 * kekirim otomatis lewat interceptor `apikey` bawaan.
 */
interface ZenimeAdminApi {

    @GET("functions/v1/zenime-admin-get-role")
    suspend fun getMyRole(
        @Header("Authorization") authorization: String
    ): RoleInfoResponse

    @GET("functions/v1/zenime-admin-list-roles")
    suspend fun listRoles(
        @Header("Authorization") authorization: String
    ): RoleListResponse

    /** Dipakai tab "Semua User" -- daftar/pencarian semua user (bukan cuma pemegang role). */
    @GET("functions/v1/zenime-admin-list-users")
    suspend fun listUsers(
        @Header("Authorization") authorization: String,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): UserListResponse

    @POST("functions/v1/zenime-admin-set-role")
    suspend fun setRole(
        @Header("Authorization") authorization: String,
        @Body body: SetRoleRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-admin-ban-user")
    suspend fun banUser(
        @Header("Authorization") authorization: String,
        @Body body: TargetUidRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-admin-unban-user")
    suspend fun unbanUser(
        @Header("Authorization") authorization: String,
        @Body body: TargetUidRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-admin-ban-device")
    suspend fun banDevice(
        @Header("Authorization") authorization: String,
        @Body body: TargetUidRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-admin-unban-device")
    suspend fun unbanDevice(
        @Header("Authorization") authorization: String,
        @Body body: DeviceIdRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-admin-delete-message")
    suspend fun deleteMessage(
        @Header("Authorization") authorization: String,
        @Body body: MessageIdRequest
    ): AdminActionResponse

    @POST("functions/v1/zenime-check-ban")
    suspend fun checkBan(
        @Header("Authorization") authorization: String,
        @Body body: DeviceIdRequest
    ): CheckBanResponse

    // --- Baca langsung lewat PostgREST (SELECT publik, RLS di SQL) ---
    // Dipakai buat nampilin badge role (centang berwarna) di Chat Global --
    // gak butuh token khusus, cuma anon key (dari interceptor global).
    @GET("rest/v1/user_roles")
    suspend fun getRolesForUids(
        @Query("firebase_uid") firebaseUidIn: String, // format: "in.(uid1,uid2,...)"
        @Query("select") select: String = "firebase_uid,role,badge_color"
    ): List<RoleListEntry>
}
