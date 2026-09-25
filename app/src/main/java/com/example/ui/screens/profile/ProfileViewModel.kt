package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ClanRepository
import com.example.data.repository.PremiumRepository
import com.example.util.AvatarUploader
import com.example.util.BannerUploader
import com.example.util.friendlyErrorMessage
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
    val favorites: List<FavoriteEntity> = emptyList(),
    val history: List<WatchHistoryEntity> = emptyList(),

    // Dialog "Edit Profil".
    val isEditDialogOpen: Boolean = false,
    val isSavingUsername: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val editError: String? = null
)

/**
 * ViewModel buat [ProfileScreen] -- ambil profil (`chat_profiles`, sama tabel
 * yang dipakai fitur Chat Global) + status premium + favorit/riwayat lokal.
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
    private val firebaseUid: String,
    private val fallbackUsername: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(username = fallbackUsername))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.favorites, repository.watchHistory) { favs, hist -> favs to hist }
                .collect { (favs, hist) ->
                    _uiState.value = _uiState.value.copy(favorites = favs, history = hist)
                }
        }
        loadProfileAndPremium()
    }

    private fun loadProfileAndPremium() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Profil & status Premium gak saling butuh -- ditarik BARENGAN,
            // bukan satu-satu, biar halaman Profil gak nunggu 2 round-trip
            // berturut-turut cuma buat nampilin header.
            val profileDeferred = async { runCatching { chatRepository.getProfile(firebaseUid) }.getOrNull() }
            val premiumDeferred = async { premiumRepository.checkPremiumStatus(firebaseUid) }
            val clanTagDeferred = async { clanRepository.getClanTagsForUids(listOf(firebaseUid)) }

            val profile = profileDeferred.await()
            val premiumResult = premiumDeferred.await()
            val isPremium = premiumResult.getOrNull()?.isPremium ?: false
            val clanTag = clanTagDeferred.await().getOrNull()?.get(firebaseUid)

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
                clanTag = clanTag
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
                    usernameColor = _uiState.value.usernameColor
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
                    usernameColor = _uiState.value.usernameColor
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
                    usernameColor = _uiState.value.usernameColor
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

    // --- Riwayat Tontonan ---

    fun clearAllHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
