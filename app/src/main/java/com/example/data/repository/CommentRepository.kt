package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.EpisodeComment
import com.example.data.model.EpisodeCommentInsert

/**
 * Repository buat fitur Komentar Episode -- baca & kirim komentar lewat
 * tabel `episode_comments` di Supabase (PostgREST langsung, pola sama
 * persis kayak [ChatRepository] buat Chat Global).
 */
class CommentRepository(
    private val api: com.example.data.api.ZenimeSupabaseApi = SupabaseNetworkModule.api
) {
    /**
     * Ambil SEMUA komentar (top-level + balasan) punya 1 episode, langsung
     * disusun jadi pohon thread di sini biar UI/ViewModel gak perlu ngurus
     * grouping-nya sendiri.
     */
    suspend fun getComments(episodeId: String): CommentThreadResult {
        val rows = api.getEpisodeComments(episodeIdEq = "eq.$episodeId")
        val topLevel = rows.filter { it.parentId == null }
            .sortedByDescending { it.createdAt }
        val repliesByParent = rows.filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.sortedBy { it.createdAt } }
        return CommentThreadResult(
            topLevel = topLevel,
            repliesByParent = repliesByParent,
            totalCount = rows.size
        )
    }

    suspend fun postComment(
        episodeId: String,
        animeId: String,
        firebaseUid: String,
        username: String,
        avatarUrl: String?,
        comment: String,
        parentId: Long? = null,
        replyToUsername: String? = null,
        isPinned: Boolean = false
    ): EpisodeComment {
        val result = api.postEpisodeComment(
            EpisodeCommentInsert(
                episodeId = episodeId,
                animeId = animeId,
                firebaseUid = firebaseUid,
                username = username,
                avatarUrl = avatarUrl,
                comment = comment,
                parentId = parentId,
                replyToUsername = replyToUsername,
                isPinned = isPinned
            )
        )
        return result.first()
    }

    /** Hapus komentar/balasan milik sendiri -- firebase_uid ikut difilter di query. */
    suspend fun deleteComment(id: Long, firebaseUid: String) {
        val response = api.deleteEpisodeComment(
            idEq = "eq.$id",
            firebaseUidEq = "eq.$firebaseUid"
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("Gagal menghapus komentar (${response.code()})")
        }
    }
}

/** Hasil [CommentRepository.getComments], udah dalam bentuk pohon thread. */
data class CommentThreadResult(
    val topLevel: List<EpisodeComment>,
    val repliesByParent: Map<Long, List<EpisodeComment>>,
    val totalCount: Int
)
