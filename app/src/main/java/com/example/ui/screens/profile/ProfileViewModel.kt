package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.EpisodeComment
import com.example.data.model.FriendRelation
import com.example.data.model.PublicFavoriteRow
import com.example.data.model.PublicWatchHistoryRow
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ClanRepository
import com.example.data.repository.CommentRepository
import com.example.data.repository.FriendRepository
import com.example.data.repository.PremiumRepository
import com.example.data.repository.PublicProfileRepository
import com.example.util.AvatarUploader
import com.example.util.BannerUploader
import com.example.util.friendlyErrorMessage
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val MAX_USERNAME_LENGTH = 24

data class ProfileUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    // Warna custom username (hex) -- disimpen di sini juga biar saveUsername/
    // uploadAvatar/uploadBanner dari halaman Profil ini gak nge-null-in warna
    // yang udah di-set user lewat dialog "Edit Profil" di Chat Global.
    val usernameColor: String? = null,
    val isPremium: Boolean = false,
    // ID urut user ("ID #123") sama tag Clan ("badge clan yang sudah ada
    // di Chat Global/Leaderboard") -- tampil di hero section ProfileScreen,
    // gantiin centang biru + badge "Premium" yang lama.
    val userNumber: Long? = null,
    val clanTag: String? = null,

    // true kalau ini profil MILIK SENDIRI (firebaseUid == viewedFirebaseUid).
    // Kalau false, `favorites`/`history` diisi dari Edge Function (baca punya
    // orang lain) bukan dari Room, dan toggle privasi gak bisa diedit.
    val isOwnProfile: Boolean = true,

    // Sumber favorit/riwayat buat DITAMPILKAN -- lokal (Room) kalau profil
    // sendiri, hasil Edge Function kalau profil orang lain.
    val favorites: List<FavoriteEntity> = emptyList(),
    val history: List<WatchHistoryEntity> = emptyList(),
    val publicFavorites: List<PublicFavoriteRow> = emptyList(),
    val publicHistory: List<PublicWatchHistoryRow> = emptyList(),

    // Toggle privasi -- punya SENDIRI (bisa diedit lewat setFavoritesPublic/
    // setHistoryPublic) kalau isOwnProfile, atau punya USER YANG DILIHAT
    // (read-only, dipakai buat mutusin nampilin list atau pesan "privat").
    val favoritesPublic: Boolean = false,
    val historyPublic: Boolean = false,
    val isLoadingPublicContent: Boolean = false,

    // Tab "Komentar" -- lazy-load, cuma ditarik pas tab-nya pertama kali
    // dibuka (bukan bareng data lain pas Profil ke-buka), biar gak nembak
    // network call yang belum tentu kepake.
    val comments: List<EpisodeComment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val hasLoadedComments: Boolean = false,
    val commentsError: String? = null,
    // Total komentar user (stat di hero profil) -- dihitung server-side,
    // terpisah dari `comments` yang cuma 50 terakhir & lazy-load.
    val commentCount: Int = 0,

    // Add Friend -- cuma kepake di profil ORANG LAIN. null = belum ke-load.
    val friendRelation: FriendRelation? = null,
    val isFriendActionInFlight: Boolean = false,
    val friendError: String? = null,

    // Dialog "Edit Profil".
    val isEditDialogOpen: Boolean = false,
    val isSavingUsername: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val editError: String? = null
)

/**
 * ViewModel buat [ProfileScreen] -- ambil profil (`chat_profiles`, sama tabel
 * yang dipakai fitur Chat Global) + status premium + favorit/riwayat.
 *
 * Dukung 2 mode:
 * - Profil SENDIRI (`viewedFirebaseUid` null atau == `firebaseUid`): favorit/
 *   riwayat diambil dari Room (repository.favorites/watchHistory) kayak
 *   sebelumnya, dan user bisa ubah toggle privasi (favoritesPublic/
 *   historyPublic) lewat [setFavoritesPublic]/[setHistoryPublic].
 * - Profil ORANG LAIN (`viewedFirebaseUid` beda dari `firebaseUid`): favorit/
 *   riwayat diambil SEKALI lewat PostgREST langsung (RLS-gated)
 *   (lihat [PublicProfileRepository.getPublicContent]), dan cuma keisi kalau
 *   toggle privasi user itu nyala -- kalau enggak, list-nya kosong tapi
 *   `favoritesPublic`/`historyPublic` di state bakal false, dipakai
 *   ProfileScreen buat nampilin pesan "akun ini privat".
 *
 * Upload avatar BEBAS buat semua user (gak perlu premium). Upload banner
 * TETEP dibatasi khusus member Premium. Kalau premium user habis masa
 * aktifnya, banner custom yang udah diupload tetap ADA di server tapi gak
 * ditampilin lagi (revert ke fallback) -- avatar custom tetap tampil
 * kapan pun, gak peduli status premium.
 */
class ProfileViewModel(
    private val repository: AnimeRepository,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val clanRepository: ClanRepository = ClanRepository(),
    private val commentRepository: CommentRepository = CommentRepository(),
    private val publicProfileRepository: PublicProfileRepository = PublicProfileRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val firebaseUid: String,
    private val fallbackUsername: String,
    // null = lihat profil sendiri. Isi uid user lain buat lihat profil mereka.
    viewedFirebaseUid: String? = null
) : ViewModel() {

    private val targetFirebaseUid: String = viewedFirebaseUid ?: firebaseUid
    private val isOwnProfile: Boolean = targetFirebaseUid == firebaseUid

    private val _uiState = MutableStateFlow(
        ProfileUiState(username = fallbackUsername, isOwnProfile = isOwnProfile)
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        if (isOwnProfile) {
            viewModelScope.launch {
                combine(repository.favorites, repository.watchHistory) { favs, hist -> favs to hist }
                    .collect { (favs, hist) ->
                        _uiState.value = _uiState.value.copy(favorites = favs, history = hist)
                    }
            }
        } else {
            loadPublicContent()
            loadFriendRelation()
        }
        loadCommentCount()
        loadProfileAndPremium()
    }

    // --- Add Friend (profil orang lain) ---

    private fun loadFriendRelation() {
        viewModelScope.launch {
            friendRepository.getRelation(firebaseUid, targetFirebaseUid)
                .onSuccess { _uiState.value = _uiState.value.copy(friendRelation = it) }
                .onFailure { e ->
                    // JANGAN sembunyiin tombolnya diem-diem kalau cek status gagal --
                    // tampilin tombol + kasih tau errornya, biar ketahuan penyebabnya.
                    val detail = if (e is HttpException) {
                        "Gagal cek status teman (HTTP ${e.code()}). Pastikan friends_setup.sql sudah dijalankan."
                    } else {
                        friendlyErrorMessage(e, "Gagal cek status teman")
                    }
                    _uiState.value = _uiState.value.copy(
                        friendRelation = FriendRelation.None,
                        friendError = detail
                    )
                }
        }
    }

    private fun runFriendAction(action: suspend () -> Result<Unit>) {
        if (isOwnProfile || _uiState.value.isFriendActionInFlight) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFriendActionInFlight = true, friendError = null)
            val result = action()
            // Selalu sinkron ulang dari server -- kalau gagal karena dia kirim
            // duluan barengan, tombolnya langsung berubah ke status yang bener.
            val relation = friendRepository.getRelation(firebaseUid, targetFirebaseUid).getOrNull()
            _uiState.value = _uiState.value.copy(
                isFriendActionInFlight = false,
                friendRelation = relation ?: _uiState.value.friendRelation,
                friendError = result.exceptionOrNull()?.let { friendlyErrorMessage(it, "Gagal memproses permintaan teman") }
            )
        }
    }

    fun sendFriendRequest() = runFriendAction { friendRepository.sendRequest(firebaseUid, targetFirebaseUid) }

    fun acceptFriendRequest() {
        val rel = _uiState.value.friendRelation as? FriendRelation.IncomingPending ?: return
        runFriendAction { friendRepository.accept(rel.friendshipId) }
    }

    /** Tolak permintaan masuk / batalin permintaan keluar / hapus teman. */
    fun removeFriendship() {
        val id = when (val rel = _uiState.value.friendRelation) {
            is FriendRelation.IncomingPending -> rel.friendshipId
            is FriendRelation.OutgoingPending -> rel.friendshipId
            is FriendRelation.Friends -> rel.friendshipId
            else -> return
        }
        runFriendAction { friendRepository.remove(id) }
    }

    fun clearFriendError() {
        _uiState.value = _uiState.value.copy(friendError = null)
    }

    private fun loadPublicContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPublicContent = true)
            publicProfileRepository.getPublicContent(targetFirebaseUid)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPublicContent = false,
                        favoritesPublic = response.favoritesPublic,
                        historyPublic = response.historyPublic,
                        publicFavorites = response.favorites.orEmpty(),
                        publicHistory = response.history.orEmpty()
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingPublicContent = false)
                }
        }
    }

    private fun loadProfileAndPremium() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Profil & status Premium gak saling butuh -- ditarik BARENGAN,
            // bukan satu-satu, biar halaman Profil gak nunggu 2 round-trip
            // berturut-turut cuma buat nampilin header.
            val profileDeferred = async { runCatching { chatRepository.getProfile(targetFirebaseUid) }.getOrNull() }
            val premiumDeferred = async { premiumRepository.checkPremiumStatus(targetFirebaseUid) }
            val clanTagDeferred = async { clanRepository.getClanTagsForUids(listOf(targetFirebaseUid)) }

            val profile = profileDeferred.await()
            val premiumResult = premiumDeferred.await()
            val isPremium = premiumResult.getOrNull()?.isPremium ?: false
            val clanTag = clanTagDeferred.await().getOrNull()?.get(targetFirebaseUid)

            // Avatar sekarang BEBAS semua user (gak perlu premium) --
            // banner tetap premium-only. Foto custom avatar selalu dipasang
            // kalau ada, gak peduli status premium sekarang.
            val resolvedAvatarUrl = profile?.avatarUrl
            val resolvedBannerUrl = if (isPremium) profile?.bannerUrl else null

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                username = profile?.username?.ifBlank { fallbackUsername } ?: fallbackUsername,
                avatarUrl = resolvedAvatarUrl,
                bannerUrl = resolvedBannerUrl,
                usernameColor = profile?.usernameColor,
                isPremium = isPremium,
                userNumber = profile?.userNumber,
                clanTag = clanTag,
                // Buat profil sendiri, ini toggle yang bisa diedit. Buat
                // profil orang lain, bakal ke-override lagi sama respons
                // Edge Function di loadPublicContent() (sumber yang bener
                // buat mutusin tampil/enggaknya list, karena SELECT langsung
                // ke chat_profiles ini kepake juga buat header/username jadi
                // gak dijamin nyampur sama hasil cek privasi Edge Function).
                favoritesPublic = if (isOwnProfile) (profile?.favoritesPublic ?: false) else _uiState.value.favoritesPublic,
                historyPublic = if (isOwnProfile) (profile?.historyPublic ?: false) else _uiState.value.historyPublic
            )
        }
    }

    // --- Edit Profil ---

    fun openEditDialog() {
        _uiState.value = _uiState.value.copy(isEditDialogOpen = true, editError = null)
    }

    fun closeEditDialog() {
        _uiState.value = _uiState.value.copy(isEditDialogOpen = false, editError = null)
    }

    fun notifyBannerRequiresPremium() {
        _uiState.value = _uiState.value.copy(
            editError = "Upload banner profil khusus buat member Premium"
        )
    }

    fun saveUsername(newUsername: String) {
        val trimmed = newUsername.trim().take(MAX_USERNAME_LENGTH)
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(editError = "Username gak boleh kosong")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingUsername = true, editError = null)
            try {
                val saved = chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = trimmed,
                    avatarUrl = _uiState.value.avatarUrl,
                    bannerUrl = _uiState.value.bannerUrl,
                    usernameColor = _uiState.value.usernameColor,
                    favoritesPublic = _uiState.value.favoritesPublic,
                    historyPublic = _uiState.value.historyPublic
                )
                _uiState.value = _uiState.value.copy(
                    isSavingUsername = false,
                    username = saved.username
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingUsername = false,
                    editError = friendlyErrorMessage(e, "Gagal menyimpan username")
                )
            }
        }
    }

    /** Upload foto profil baru -- BEBAS semua user, gak ada pengecekan premium lagi. */
    fun uploadAvatar(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true, editError = null)
            try {
                val url = AvatarUploader.uploadAvatar(context, imageUri, firebaseUid)
                val saved = chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.username,
                    avatarUrl = url,
                    bannerUrl = _uiState.value.bannerUrl,
                    usernameColor = _uiState.value.usernameColor,
                    favoritesPublic = _uiState.value.favoritesPublic,
                    historyPublic = _uiState.value.historyPublic
                )
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    avatarUrl = saved.avatarUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    editError = friendlyErrorMessage(e, "Gagal upload foto profil")
                )
            }
        }
    }

    /** Upload banner baru -- khusus Premium, sama kayak avatar. */
    fun uploadBanner(context: Context, imageUri: Uri) {
        if (!_uiState.value.isPremium) {
            notifyBannerRequiresPremium()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingBanner = true, editError = null)
            try {
                val url = BannerUploader.uploadBanner(context, imageUri, firebaseUid)
                val saved = chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.username,
                    avatarUrl = _uiState.value.avatarUrl,
                    bannerUrl = url,
                    usernameColor = _uiState.value.usernameColor,
                    favoritesPublic = _uiState.value.favoritesPublic,
                    historyPublic = _uiState.value.historyPublic
                )
                _uiState.value = _uiState.value.copy(
                    isUploadingBanner = false,
                    bannerUrl = saved.bannerUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingBanner = false,
                    editError = friendlyErrorMessage(e, "Gagal upload banner")
                )
            }
        }
    }

    // --- Toggle privasi Favorit/Riwayat (cuma berlaku di profil sendiri) ---

    fun setFavoritesPublic(isPublic: Boolean) {
        if (!isOwnProfile) return
        val previous = _uiState.value.favoritesPublic
        _uiState.value = _uiState.value.copy(favoritesPublic = isPublic)
        viewModelScope.launch {
            try {
                chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.username,
                    avatarUrl = _uiState.value.avatarUrl,
                    bannerUrl = _uiState.value.bannerUrl,
                    usernameColor = _uiState.value.usernameColor,
                    favoritesPublic = isPublic,
                    historyPublic = _uiState.value.historyPublic
                )
            } catch (e: Exception) {
                // Gagal simpan ke server -- balikin toggle-nya biar UI gak
                // bohong soal status privasi yang sebenarnya kesimpen.
                _uiState.value = _uiState.value.copy(
                    favoritesPublic = previous,
                    editError = friendlyErrorMessage(e, "Gagal menyimpan pengaturan privasi")
                )
            }
        }
    }

    fun setHistoryPublic(isPublic: Boolean) {
        if (!isOwnProfile) return
        val previous = _uiState.value.historyPublic
        _uiState.value = _uiState.value.copy(historyPublic = isPublic)
        viewModelScope.launch {
            try {
                chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.username,
                    avatarUrl = _uiState.value.avatarUrl,
                    bannerUrl = _uiState.value.bannerUrl,
                    usernameColor = _uiState.value.usernameColor,
                    favoritesPublic = _uiState.value.favoritesPublic,
                    historyPublic = isPublic
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    historyPublic = previous,
                    editError = friendlyErrorMessage(e, "Gagal menyimpan pengaturan privasi")
                )
            }
        }
    }

    // --- Riwayat Tontonan ---

    fun clearAllHistory() {
        if (!isOwnProfile) return
        viewModelScope.launch { repository.clearHistory() }
    }

    // --- Tab "Komentar" ---

    /** Dipanggil sekali pas tab "Komentar" pertama kali dibuka (lihat [hasLoadedComments]). */
    fun loadMyCommentsIfNeeded() {
        if (_uiState.value.hasLoadedComments || _uiState.value.isLoadingComments) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingComments = true, commentsError = null)
            runCatching { commentRepository.getMyComments(targetFirebaseUid) }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingComments = false,
                        hasLoadedComments = true,
                        comments = list
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingComments = false,
                        commentsError = friendlyErrorMessage(e, "Gagal memuat komentar")
                    )
                }
        }
    }

    private fun loadCommentCount() {
        viewModelScope.launch {
            runCatching { commentRepository.getMyCommentCount(targetFirebaseUid) }
                .getOrNull()
                ?.let { count -> _uiState.value = _uiState.value.copy(commentCount = count) }
        }
    }
}
