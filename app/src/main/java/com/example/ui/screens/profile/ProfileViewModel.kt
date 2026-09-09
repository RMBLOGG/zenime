package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.PremiumRepository
import com.example.util.AvatarUploader
import com.example.util.BannerUploader
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val MAX_USERNAME_LENGTH = 24

data class ProfileUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val isPremium: Boolean = false,
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
 * Upload avatar & banner SAMA-SAMA dibatasi khusus member Premium (banner
 * tambahan baru: bisa upload foto sendiri, sebelumnya cuma auto dari
 * thumbnail bookmark/history pertama). Kalau premium user habis masa
 * aktifnya, banner/avatar custom yang udah diupload tetap ADA di server tapi
 * gak ditampilin lagi (revert ke fallback) -- sama persis kayak perilaku
 * avatar di Chat Global, biar konsisten.
 */
class ProfileViewModel(
    private val repository: AnimeRepository,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
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

            val profile = try {
                chatRepository.getProfile(firebaseUid)
            } catch (e: Exception) {
                null
            }

            val premiumResult = premiumRepository.checkPremiumStatus(firebaseUid)
            val isPremium = premiumResult.getOrNull()?.isPremium ?: false

            // Sama kayak avatar di Chat Global: foto custom (avatar & banner)
            // cuma dipasang kalau user-nya masih premium sekarang.
            val resolvedAvatarUrl = if (isPremium) profile?.avatarUrl else null
            val resolvedBannerUrl = if (isPremium) profile?.bannerUrl else null

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                username = profile?.username?.ifBlank { fallbackUsername } ?: fallbackUsername,
                avatarUrl = resolvedAvatarUrl,
                bannerUrl = resolvedBannerUrl,
                isPremium = isPremium
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

    fun notifyAvatarRequiresPremium() {
        _uiState.value = _uiState.value.copy(
            editError = "Upload foto profil khusus buat member Premium"
        )
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
                    bannerUrl = _uiState.value.bannerUrl
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

    /** Upload foto profil baru -- pengecekan premium diulang di sini juga, bukan cuma di UI. */
    fun uploadAvatar(context: Context, imageUri: Uri) {
        if (!_uiState.value.isPremium) {
            notifyAvatarRequiresPremium()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true, editError = null)
            try {
                val url = AvatarUploader.uploadAvatar(context, imageUri, firebaseUid)
                val saved = chatRepository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.username,
                    avatarUrl = url,
                    bannerUrl = _uiState.value.bannerUrl
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
                    bannerUrl = url
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
