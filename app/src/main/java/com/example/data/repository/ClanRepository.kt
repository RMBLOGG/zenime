package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeClanApi
import com.example.data.model.Clan
import com.example.data.model.ClanDonationEntry
import com.example.data.model.ClanIdRequest
import com.example.data.model.ClanMember
import com.example.data.model.ClanMemberDisplay
import com.example.data.model.ChatProfile
import com.example.data.model.CreateClanRequest
import com.example.data.model.DonateToClanRequest
import com.example.data.model.KickMemberRequest
import com.example.data.model.RespondJoinRequestBody
import com.example.data.model.UpdateClanSettingsRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repository fitur Clan.
 *
 * Baca data publik (detail clan, daftar member, log donasi) langsung lewat
 * PostgREST -- gak butuh token khusus, RLS-nya emang public SELECT (lihat
 * SQL schema-nya).
 *
 * Aksi yang nyentuh identitas/uang (create, join, donate, kick, dst) lewat
 * Edge Function, dan WAJIB nempelin Firebase ID Token asli sebagai header
 * Authorization -- itu yang dilakuin [authHeader] di bawah. Jangan pernah
 * kirim firebase_uid ke Edge Function itu cuma dari body request tanpa token,
 * karena Edge Function-nya nolak (dan justru itu tujuannya).
 */
class ClanRepository(
    private val clanApi: ZenimeClanApi = SupabaseNetworkModule.clanApi,
    private val chatRepository: ChatRepository = ChatRepository()
) {

    /** Ambil Firebase ID Token yang lagi aktif, format siap pakai buat header Authorization. */
    private suspend fun authHeader(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Kamu harus login dulu")
        val token = user.getIdToken(false).await()?.token
            ?: throw IllegalStateException("Gagal ambil Firebase ID Token, coba login ulang")
        return "Bearer $token"
    }

    // --- Baca ---

    suspend fun getClan(clanId: String): Result<Clan> = runCatching {
        clanApi.getClanById(idEq = "eq.$clanId").first()
    }

    /** Clan yang lagi diikutin user sekarang (kalau ada) -- buat cek "udah gabung clan lain". */
    suspend fun getMyMembership(firebaseUid: String): Result<ClanMember?> = runCatching {
        clanApi.getMembershipByUid(firebaseUidEq = "eq.$firebaseUid").firstOrNull()
    }

    suspend fun getMyJoinRequestPending(clanId: String): Result<Boolean> = runCatching {
        clanApi.getMyJoinRequestStatus(authorization = authHeader(), clanId = clanId).pending
    }

    /** Daftar member digabung sama username/avatar dari chat_profiles. */
    suspend fun getMembers(clanId: String): Result<List<ClanMemberDisplay>> = runCatching {
        val members = clanApi.getClanMembers(clanIdEq = "eq.$clanId")
        mergeWithProfiles(members)
    }

    /**
     * Ranking "Donasi Hari Ini" -- ambil log donasi hari ini (waktu device),
     * jumlahin per user, urutin dari yang paling besar.
     */
    suspend fun getTodayDonations(clanId: String): Result<List<ClanDonationEntry>> = runCatching {
        val startOfToday = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val rows = clanApi.getDonationLog(clanIdEq = "eq.$clanId", createdAtGte = "gte.$startOfToday")

        val grouped = rows.groupBy { it.firebaseUid }
            .map { (uid, entries) ->
                uid to (entries.sumOf { it.amount } to entries.size)
            }

        val members = clanApi.getClanMembers(clanIdEq = "eq.$clanId")
        val roleByUid = members.associate { it.firebaseUid to it.role }
        val profiles = fetchProfiles(grouped.map { it.first })

        grouped.map { (uid, amountAndCount) ->
            val profile = profiles[uid]
            ClanDonationEntry(
                firebaseUid = uid,
                username = profile?.username ?: "Pengguna",
                avatarUrl = profile?.avatarUrl,
                role = roleByUid[uid] ?: "member",
                amountToday = amountAndCount.first,
                donationCountToday = amountAndCount.second
            )
        }.sortedByDescending { it.amountToday }
    }

    /** Daftar semua clan -- dasar buat halaman Browse & Leaderboard (dua-duanya pakai data yang sama, beda urutan/framing aja). */
    suspend fun browseClans(): Result<List<Clan>> = runCatching {
        clanApi.browseClans()
    }

    private suspend fun mergeWithProfiles(members: List<ClanMember>): List<ClanMemberDisplay> {
        val profiles = fetchProfiles(members.map { it.firebaseUid })
        return members.map { member ->
            val profile = profiles[member.firebaseUid]
            ClanMemberDisplay(
                firebaseUid = member.firebaseUid,
                role = member.role,
                totalContribution = member.totalContribution,
                joinedAt = member.joinedAt,
                username = profile?.username ?: "Pengguna",
                avatarUrl = profile?.avatarUrl
            )
        }
    }

    /** Batch-fetch chat_profiles buat sekumpulan uid, dipetakan per firebase_uid. */
    private suspend fun fetchProfiles(uids: List<String>): Map<String, ChatProfile> {
        if (uids.isEmpty()) return emptyMap()
        return uids.distinct()
            .mapNotNull { uid -> chatRepository.getProfile(uid)?.let { uid to it } }
            .toMap()
    }

    // --- Aksi (Edge Function) ---
    //
    // CATATAN: Retrofit otomatis throw HttpException buat response non-2xx
    // (400/401/403/dst), JADI BUKAN dikembaliin sebagai body ClanActionResponse
    // biasa. Makanya tiap aksi di bawah nangkep HttpException secara eksplisit
    // dan narik pesan error asli dari body-nya -- kalau enggak, orang cuma
    // bakal liat "HTTP 400" doang tanpa tau kenapa (misal "ZCoin tidak cukup").

    suspend fun createClan(name: String, tag: String, photoUrl: String?): Result<Clan> = runCatching {
        try {
            val response = clanApi.createClan(authHeader(), CreateClanRequest(name, tag, photoUrl))
            response.clan ?: throw IllegalStateException("Gagal bikin clan")
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal bikin clan"))
        }
    }

    suspend fun submitJoinRequest(clanId: String): Result<Unit> = runCatching {
        try {
            clanApi.submitJoinRequest(authHeader(), ClanIdRequest(clanId))
            Unit
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal mengirim request join"))
        }
    }

    suspend fun respondJoinRequest(requestId: String, approve: Boolean): Result<Unit> = runCatching {
        try {
            clanApi.respondJoinRequest(authHeader(), RespondJoinRequestBody(requestId, approve))
            Unit
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal memproses request join"))
        }
    }

    /** Daftar pending join request buat clan ini -- cuma bisa dibaca leader/co-leader (dicek di Edge Function). */
    suspend fun getPendingJoinRequests(clanId: String): Result<List<com.example.data.model.PendingJoinRequestDisplay>> = runCatching {
        try {
            val response = clanApi.getPendingJoinRequests(authHeader(), clanId)
            val profiles = fetchProfiles(response.requests.map { it.firebaseUid })
            response.requests.map { item ->
                val profile = profiles[item.firebaseUid]
                com.example.data.model.PendingJoinRequestDisplay(
                    requestId = item.id,
                    firebaseUid = item.firebaseUid,
                    username = profile?.username ?: "Pengguna",
                    avatarUrl = profile?.avatarUrl,
                    requestedAt = item.requestedAt
                )
            }
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal ambil daftar request join"))
        }
    }

    suspend fun donateToClan(clanId: String, amount: Long): Result<Clan> = runCatching {
        try {
            val response = clanApi.donateToClan(authHeader(), DonateToClanRequest(clanId, amount))
            response.clan ?: throw IllegalStateException("Gagal donasi")
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal donasi ZCoin"))
        }
    }

    suspend fun updateClanSettings(
        clanId: String,
        name: String? = null,
        tag: String? = null,
        photoUrl: String? = null
    ): Result<Clan> = runCatching {
        try {
            val response = clanApi.updateClanSettings(
                authHeader(),
                UpdateClanSettingsRequest(clanId, name, tag, photoUrl)
            )
            response.clan ?: throw IllegalStateException("Gagal update settingan clan")
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal update settingan clan"))
        }
    }

    suspend fun kickMember(clanId: String, targetUid: String): Result<Unit> = runCatching {
        try {
            clanApi.kickMember(authHeader(), KickMemberRequest(clanId, targetUid))
            Unit
        } catch (e: HttpException) {
            throw IllegalStateException(extractErrorMessage(e, "Gagal kick member"))
        }
    }

    /** Narik field "error" dari body JSON response gagal (dikirim Edge Function). */
    private fun extractErrorMessage(e: HttpException, fallback: String): String {
        val raw = e.response()?.errorBody()?.string()
        val parsed = raw?.let { body ->
            Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        }
        return parsed ?: fallback
    }
}
