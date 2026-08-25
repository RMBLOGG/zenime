package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.repository.ChatRepository
import com.example.data.repository.PremiumRepository
import com.example.util.AvatarUploader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val COOLDOWN_SECONDS = 5
private const val POLL_INTERVAL_MS = 3000L
private const val MAX_MESSAGE_LENGTH = 300
private const val MAX_USERNAME_LENGTH = 24

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val errorMessage: String? = null,

    // Profil (nama & avatar) yang lagi dipakai buat kirim pesan.
    val displayUsername: String = "",
    val displayAvatarUrl: String? = null,
    val isPremium: Boolean = false,

    // State buat dialog "Edit Profil".
    val isProfileDialogOpen: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val profileError: String? = null
)

/**
 * Cooldown 5 detik dihitung MURNI di client (gak ada tabel/kolom rate-limit
 * di server) -- cukup buat nyegah spam kasual dari UI normal. Kalau nanti
 * mau lebih ketat (misal cegah orang yang modif APK/panggil API langsung),
 * itu butuh pengecekan tambahan di server (contoh: Edge Function yang cek
 * timestamp pesan terakhir per firebase_uid sebelum insert).
 *
 * Username & avatar chat: username bisa diganti SEMUA user (disimpan di
 * tabel `chat_profiles`, override nama dari akun Google). Avatar custom
 * (upload dari galeri) DIBATASI khusus user Premium -- dicek lewat
 * PremiumRepository sebelum ngizinin upload; user non-premium tetap pakai
 * foto profil Google-nya.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val premiumRepository: PremiumRepository,
    private val firebaseUid: String,
    fallbackUsername: String,
    private val fallbackAvatarUrl: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            displayUsername = fallbackUsername,
            displayAvatarUrl = fallbackAvatarUrl
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        loadProfileAndPremiumStatus(fallbackUsername)
        startPolling()
    }

    private fun loadProfileAndPremiumStatus(fallbackUsername: String) {
        viewModelScope.launch {
            val profile = try {
                repository.getProfile(firebaseUid)
            } catch (e: Exception) {
                null
            }
            if (profile != null) {
                _uiState.value = _uiState.value.copy(
                    displayUsername = profile.username.ifBlank { fallbackUsername },
                    // Avatar custom (premium) menang; kalau belum pernah upload, tetap
                    // pakai foto Google bawaan.
                    displayAvatarUrl = profile.avatarUrl ?: fallbackAvatarUrl
                )
            }

            premiumRepository.checkPremiumStatus(firebaseUid)
                .onSuccess { status ->
                    _uiState.value = _uiState.value.copy(isPremium = status.isPremium)
                }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                refreshMessages()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshMessages() {
        try {
            val messages = repository.getMessages()
            _uiState.value = _uiState.value.copy(
                messages = messages,
                isLoading = false,
                errorMessage = null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = _uiState.value.errorMessage ?: (e.localizedMessage ?: "Gagal memuat chat")
            )
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.cooldownSeconds > 0 || _uiState.value.isSending) return

        val safeText = trimmed.take(MAX_MESSAGE_LENGTH)
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
            try {
                repository.sendMessage(
                    firebaseUid = firebaseUid,
                    username = state.displayUsername,
                    avatarUrl = state.displayAvatarUrl,
                    message = safeText
                )
                _uiState.value = _uiState.value.copy(isSending = false)
                refreshMessages()
                startCooldown()
            } catch (e: Exception) {
                // Gagal kirim -- gak usah kena cooldown, biar user bisa langsung coba lagi.
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = e.localizedMessage ?: "Gagal mengirim pesan"
                )
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in COOLDOWN_SECONDS downTo 1) {
                _uiState.value = _uiState.value.copy(cooldownSeconds = remaining)
                delay(1000L)
            }
            _uiState.value = _uiState.value.copy(cooldownSeconds = 0)
        }
    }

    // --- Edit Profil ---

    fun openProfileDialog() {
        _uiState.value = _uiState.value.copy(isProfileDialogOpen = true, profileError = null)
    }

    fun closeProfileDialog() {
        _uiState.value = _uiState.value.copy(isProfileDialogOpen = false, profileError = null)
    }

    /** Dipanggil pas user non-premium coba tap avatar buat ganti foto. */
    fun notifyAvatarRequiresPremium() {
        _uiState.value = _uiState.value.copy(
            profileError = "Upload foto profil khusus buat member Premium"
        )
    }

    /** Simpan username baru (dibuka semua user, gak peduli premium). */
    fun saveUsername(newUsername: String) {
        val trimmed = newUsername.trim().take(MAX_USERNAME_LENGTH)
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(profileError = "Username gak boleh kosong")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, profileError = null)
            try {
                val saved = repository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = trimmed,
                    avatarUrl = _uiState.value.displayAvatarUrl
                )
                _uiState.value = _uiState.value.copy(
                    isSavingProfile = false,
                    displayUsername = saved.username,
                    isProfileDialogOpen = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingProfile = false,
                    profileError = e.localizedMessage ?: "Gagal menyimpan username"
                )
            }
        }
    }

    /**
     * Upload foto profil baru buat Chat Global. Dipanggil setelah user milih
     * gambar dari galeri -- pengecekan premium tetap diulang di sini (bukan
     * cuma di UI) biar gak bisa dilewatin dengan manggil fungsi ini langsung.
     */
    fun uploadAvatar(context: Context, imageUri: Uri) {
        if (!_uiState.value.isPremium) {
            _uiState.value = _uiState.value.copy(
                profileError = "Upload foto profil khusus buat member Premium"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true, profileError = null)
            try {
                val url = AvatarUploader.uploadAvatar(context, imageUri, firebaseUid)
                val saved = repository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.displayUsername,
                    avatarUrl = url
                )
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    displayAvatarUrl = saved.avatarUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    profileError = e.localizedMessage ?: "Gagal upload foto profil"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        cooldownJob?.cancel()
    }
}
