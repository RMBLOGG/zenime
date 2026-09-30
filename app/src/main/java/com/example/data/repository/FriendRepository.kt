package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeFriendApi
import com.example.data.model.FriendDisplay
import com.example.data.model.FriendRelation
import com.example.data.model.FriendStatus
import com.example.data.model.Friendship
import com.example.data.model.FriendshipAcceptBody
import com.example.data.model.FriendshipInsert
import retrofit2.Response
import java.time.Instant

/**
 * Repository fitur Add Friend. Semua lewat PostgREST langsung (lihat
 * backend/supabase/friends_setup.sql), gak pakai Edge Function.
 */
class FriendRepository(
    private val api: ZenimeFriendApi = SupabaseNetworkModule.friendApi,
    private val chatRepository: ChatRepository = ChatRepository()
) {

    private fun Response<Void>.requireSuccess(action: String) {
        if (!isSuccessful) {
            val body = runCatching { errorBody()?.string() }.getOrNull()
            // 409/23505 = pasangan ini udah punya baris (mis. dia kirim duluan barengan).
            throw IllegalStateException("$action gagal (HTTP ${code()}) ${body.orEmpty()}".trim())
        }
    }

    /** Hubungan [myUid] terhadap [otherUid]. */
    suspend fun getRelation(myUid: String, otherUid: String): Result<FriendRelation> = runCatching {
        val filter = "(and(requester_uid.eq.$myUid,addressee_uid.eq.$otherUid)," +
            "and(requester_uid.eq.$otherUid,addressee_uid.eq.$myUid))"
        val row = api.getFriendshipBetween(orFilter = filter).firstOrNull()
        row.toRelation(myUid)
    }

    private fun Friendship?.toRelation(myUid: String): FriendRelation = when {
        this == null -> FriendRelation.None
        status == FriendStatus.ACCEPTED -> FriendRelation.Friends(id)
        requesterUid == myUid -> FriendRelation.OutgoingPending(id)
        else -> FriendRelation.IncomingPending(id)
    }

    suspend fun sendRequest(myUid: String, otherUid: String): Result<Unit> = runCatching {
        api.sendRequest(FriendshipInsert(requesterUid = myUid, addresseeUid = otherUid))
            .requireSuccess("Kirim permintaan")
    }

    suspend fun accept(friendshipId: String): Result<Unit> = runCatching {
        api.accept(
            idEq = "eq.$friendshipId",
            body = FriendshipAcceptBody(respondedAt = Instant.now().toString())
        ).requireSuccess("Terima permintaan")
    }

    /** Tolak permintaan masuk / batalin permintaan keluar / hapus teman. */
    suspend fun remove(friendshipId: String): Result<Unit> = runCatching {
        api.delete(idEq = "eq.$friendshipId").requireSuccess("Hapus")
    }

    data class FriendLists(
        val friends: List<FriendDisplay>,
        val incoming: List<FriendDisplay>,
        val outgoing: List<FriendDisplay>
    )

    suspend fun getLists(myUid: String): Result<FriendLists> = runCatching {
        val rows = api.getMyFriendships(orFilter = "(requester_uid.eq.$myUid,addressee_uid.eq.$myUid)")
        fun Friendship.otherUid() = if (requesterUid == myUid) addresseeUid else requesterUid

        val profiles = chatRepository.getProfilesForUids(rows.map { it.otherUid() })
        fun Friendship.display(): FriendDisplay {
            val uid = otherUid()
            val p = profiles[uid]
            return FriendDisplay(
                friendshipId = id,
                firebaseUid = uid,
                username = p?.username?.ifBlank { null } ?: "Pengguna Zenime",
                avatarUrl = p?.avatarUrl
            )
        }

        FriendLists(
            friends = rows.filter { it.status == FriendStatus.ACCEPTED }.map { it.display() }
                .sortedBy { it.username.lowercase() },
            incoming = rows.filter { it.status == FriendStatus.PENDING && it.addresseeUid == myUid }.map { it.display() },
            outgoing = rows.filter { it.status == FriendStatus.PENDING && it.requesterUid == myUid }.map { it.display() }
        )
    }
}
