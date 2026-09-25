package com.example.ui.screens.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.EpisodeComment
import com.example.data.repository.ChatRepository
import com.example.data.repository.CommentRepository
import com.example.data.repository.PremiumRepository
import com.example.data.repository.XpRepository
import com.example.util.friendlyErrorMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MAX_COMMENT_LENGTH = 300

enum class CommentSortMode { TERBARU, TOP_COMMENT }

data class CommentsUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val totalCount: Int = 0,
    val sortMode: CommentSortMode = CommentSortMode.TERBARU,
    val topLevel: List<EpisodeComment> = emptyList(),
    val repliesByParent: Map<Long, List<EpisodeComment>> = emptyMap(),

    // Identitas diri sendiri, buat kirim komentar & nampilin avatar di input bar.
    val myFirebaseUid: String = "",
    val myUsername: String = "",
    val myAvatarUrl: String? = null,
    val isPremium: Boolean = false,

    // Thread "Threads" bottom sheet yang lagi kebuka (null = ketutup).
    val openThreadParentId: Long? = null,
    // Balasan yang lagi ditarget (buat nampilin "@username" di kolom input
    // balasan dalam bottom sheet Threads) -- null = bales komentar utama.
    val replyTarget: EpisodeComment? = null,
    val deletingCommentId: Long? = null,

    // Badge per firebase_uid pengirim, pola sama kayak Chat Global.
    val premiumUids: Set<String> = emptySet(),
    val levelsByUid: Map<String, Int> = emptyMap(),
    val userNumbersByUid: Map<String, Long> = emptyMap(),
    val avatarUrlsByUid: Map<String, String> = emptyMap()
) {
    /** Urutan tampil komentar top-level sesuai tab yang lagi aktif. */
    val sortedTopLevel: List<EpisodeComment>
        get() = when (sortMode) {
            CommentSortMode.TERBARU -> topLevel
            CommentSortMode.TOP_COMMENT -> topLevel.sortedWith(
                compareByDescending<EpisodeComment> { it.isPinned }
                    .thenByDescending { repliesByParent[it.id]?.size ?: 0 }
            )
        }
}

/**
 * ViewModel Komentar Episode -- 1 instance dipakai buat 1 episode
 * (episodeId/animeId dikunci pas dibikin). Identitas pengirim (uid,
 * username, avatar, status premium) ditarik dari profil Chat Global
 * (`chat_profiles`) biar konsisten sama yang dipakai di Chat/Profil --
 * user gak perlu setup identitas terpisah khusus buat komentar.
 */
class CommentsViewModel(
    private val episodeId: String,
    private val animeId: String,
    private val repository: CommentRepository = CommentRepository(),
    private val chatRepository: ChatRepository = ChatRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val xpRepository: XpRepository = XpRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val levelCache = mutableMapOf<String, Int?>()
    private val premiumCache = mutableMapOf<String, Boolean>()
    private val userNumberCache = mutableMapOf<String, Long?>()
    private val avatarCache = mutableMapOf<String, String?>()
    private val badgeCheckedUids = mutableSetOf<String>()

    init {
        loadMyIdentity()
        refresh()
    }

    private fun loadMyIdentity() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        _uiState.value = _uiState.value.copy(
            myFirebaseUid = uid,
            myUsername = user.displayName ?: "Pengguna"
        )
        viewModelScope.launch {
            val profileDeferred = async { runCatching { chatRepository.getProfile(uid) }.getOrNull() }
            val premiumDeferred = async { premiumRepository.checkPremiumStatus(uid) }
            val profile = profileDeferred.await()
            val isPremium = premiumDeferred.await().getOrNull()?.isPremium ?: false

            premiumCache[uid] = isPremium
            userNumberCache[uid] = profile?.userNumber
            avatarCache[uid] = profile?.avatarUrl
            badgeCheckedUids += uid

            _uiState.value = _uiState.value.copy(
                myUsername = profile?.username?.ifBlank { _uiState.value.myUsername } ?: _uiState.value.myUsername,
                myAvatarUrl = profile?.avatarUrl,
                isPremium = isPremium,
                premiumUids = premiumUidsSnapshot(),
                userNumbersByUid = userNumberCache.filterValues { it != null }.mapValues { it!! },
                avatarUrlsByUid = avatarCache.filterValues { it != null }.mapValues { it!! }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repository.getComments(episodeId) }
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        topLevel = result.topLevel,
                        repliesByParent = result.repliesByParent,
                        totalCount = result.totalCount
                    )
                    checkBadgesForNewSenders(result.topLevel + result.repliesByParent.values.flatten())
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = friendlyErrorMessage(e, "Gagal memuat komentar")
                    )
                }
        }
    }

    fun setSortMode(mode: CommentSortMode) {
        _uiState.value = _uiState.value.copy(sortMode = mode)
    }

    fun openThread(parentId: Long) {
        _uiState.value = _uiState.value.copy(openThreadParentId = parentId, replyTarget = null)
    }

    fun closeThread() {
        _uiState.value = _uiState.value.copy(openThreadParentId = null, replyTarget = null)
    }

    /** Set target balasan di dalam thread (null = bales komentar utama thread-nya). */
    fun setReplyTarget(target: EpisodeComment?) {
        _uiState.value = _uiState.value.copy(replyTarget = target)
    }

    /** Kirim komentar TOP-LEVEL baru (dari input utama di bawah header). */
    fun postTopLevelComment(text: String, asPremiumHighlight: Boolean) {
        postComment(text = text, parentId = null, replyToUsername = null, asPremiumHighlight = asPremiumHighlight)
    }

    /** Kirim BALASAN di dalam thread yang lagi kebuka. */
    fun postReply(text: String) {
        val threadId = _uiState.value.openThreadParentId ?: return
        val replyTo = _uiState.value.replyTarget?.username
        postComment(text = text, parentId = threadId, replyToUsername = replyTo, asPremiumHighlight = false)
    }

    private fun postComment(text: String, parentId: Long?, replyToUsername: String?, asPremiumHighlight: Boolean) {
        val trimmed = text.trim().take(MAX_COMMENT_LENGTH)
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        if (state.myFirebaseUid.isBlank() || state.isSending) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
            runCatching {
                repository.postComment(
                    episodeId = episodeId,
                    animeId = animeId,
                    firebaseUid = state.myFirebaseUid,
                    username = state.myUsername,
                    avatarUrl = state.myAvatarUrl,
                    comment = trimmed,
                    parentId = parentId,
                    replyToUsername = replyToUsername,
                    // Highlight mahkota cuma efektif buat user Premium, biarpun
                    // togglenya kepencet -- dicek ulang di sini (bukan cuma di UI)
                    // biar gak bisa disalahgunain user non-premium.
                    isPinned = asPremiumHighlight && state.isPremium
                )
            }.onSuccess { inserted ->
                if (parentId == null) {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        topLevel = listOf(inserted) + _uiState.value.topLevel,
                        totalCount = _uiState.value.totalCount + 1
                    )
                } else {
                    val updated = _uiState.value.repliesByParent.toMutableMap()
                    updated[parentId] = (updated[parentId] ?: emptyList()) + inserted
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        repliesByParent = updated,
                        replyTarget = null,
                        totalCount = _uiState.value.totalCount + 1
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSending = false, errorMessage = friendlyErrorMessage(e, "Gagal mengirim komentar"))
            }
        }
    }

    fun deleteComment(comment: EpisodeComment) {
        val uid = _uiState.value.myFirebaseUid
        if (uid.isBlank() || comment.firebaseUid != uid) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingCommentId = comment.id)
            runCatching { repository.deleteComment(comment.id, uid) }
                .onSuccess {
                    if (comment.parentId == null) {
                        _uiState.value = _uiState.value.copy(
                            topLevel = _uiState.value.topLevel.filterNot { it.id == comment.id },
                            deletingCommentId = null,
                            totalCount = (_uiState.value.totalCount - 1).coerceAtLeast(0)
                        )
                    } else {
                        val updated = _uiState.value.repliesByParent.toMutableMap()
                        updated[comment.parentId] = updated[comment.parentId].orEmpty().filterNot { it.id == comment.id }
                        _uiState.value = _uiState.value.copy(
                            repliesByParent = updated,
                            deletingCommentId = null,
                            totalCount = (_uiState.value.totalCount - 1).coerceAtLeast(0)
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        deletingCommentId = null,
                        errorMessage = friendlyErrorMessage(e, "Gagal menghapus komentar")
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun premiumUidsSnapshot(): Set<String> = premiumCache.filterValues { it }.keys.toSet()

    /** Batch-fetch level/premium/#id/avatar-terkini buat pengirim baru yang belum pernah dicek. */
    private fun checkBadgesForNewSenders(comments: List<EpisodeComment>) {
        val newUids = comments.map { it.firebaseUid }.distinct().filter { it.isNotBlank() && it !in badgeCheckedUids }
        if (newUids.isEmpty()) return
        badgeCheckedUids += newUids

        viewModelScope.launch {
            coroutineScope {
                val levelsDeferred = async { runCatching { xpRepository.getLevelsForUids(newUids) }.getOrNull()?.getOrNull() ?: emptyMap() }
                val premiumDeferred = async { premiumRepository.getPremiumStatusForUids(newUids) }
                val profilesDeferred = async { runCatching { chatRepository.getProfilesForUids(newUids) }.getOrDefault(emptyMap()) }

                val levels = levelsDeferred.await()
                val premiums = premiumDeferred.await()
                val profiles = profilesDeferred.await()

                newUids.forEach { uid ->
                    levelCache[uid] = levels[uid]
                    premiumCache[uid] = premiums[uid] ?: false
                    userNumberCache[uid] = profiles[uid]?.userNumber
                    avatarCache[uid] = profiles[uid]?.avatarUrl
                }

                _uiState.value = _uiState.value.copy(
                    premiumUids = premiumUidsSnapshot(),
                    levelsByUid = levelCache.filterValues { it != null }.mapValues { it.value!! },
                    userNumbersByUid = userNumberCache.filterValues { it != null }.mapValues { it!! },
                    avatarUrlsByUid = avatarCache.filterValues { it != null }.mapValues { it!! }
                )
            }
        }
    }
}
