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
    val profileError: String? = null,

    // Pesan yang lagi mau di-reply (null = gak lagi reply apa-apa).
    val replyTarget: ChatMessage? = null,
    // id pesan yang lagi diproses hapus, buat nampilin loading kecil di bubble-nya.
    val deletingMessageId: Long? = null,

    // Kumpulan firebase_uid pengirim yang statusnya premium -- dipakai buat
    // nampilin badge Premium di samping username di bubble chat.
    val premiumUids: Set<String> = emptySet()
)

/**
 * Cooldown 5 detik dihitung MURNI di client (gak ada tabel/kolom rate-limit
 * di server) -- cukup buat nyegah spam kasual dari UI normal. Kalau nanti
 * mau lebih ketat (misal cegah orang yang modif APK/panggil API langsung),
 * itu butuh pengecekan tambahan di server (contoh: Edge Function yang cek
 * timestamp pesan terakhir per firebase_uid sebelum insert).
 *
 * Username & avatar chat: username bisa diganti SEMUA user (disimpan di
 * tabel `chat_profiles`, override nama dari akun Google). Avatar DEFAULT
 * di chat adalah avatar auto-generate Zenime sendiri (warna + inisial,
 * lihat GeneratedAvatar.kt) -- BUKAN foto akun Google, biar gak "kebawa"
 * foto asli user yang belum tentu mau dipajang di chat publik. Avatar
 * foto asli (upload dari galeri) DIBATASI khusus user Premium.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val premiumRepository: PremiumRepository,
    private val firebaseUid: String,
    fallbackUsername: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            displayUsername = fallbackUsername,
            // null = belum ada foto custom -> UI nampilin GeneratedAvatar
            // (avatar warna + inisial), bukan foto Google.
            displayAvatarUrl = null
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    // Cache status premium per firebase_uid biar gak nge-hit zenime-check-premium
    // berkali-kali buat pengirim yang sama tiap polling (3 detik sekali). Sekali
    // dicek, hasilnya dipakai terus selama sesi chat ini kebuka.
    private val premiumStatusCache = mutableMapOf<String, Boolean>()
    private val checkedUids = mutableSetOf<String>()

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

            val premiumResult = premiumRepository.checkPremiumStatus(firebaseUid)
            val isPremium = premiumResult.getOrNull()?.isPremium ?: false

            // Foto custom (hasil upload) cuma dipasang kalau user-nya masih
            // premium. Kalau enggak (baik belum pernah upload, maupun udah
            // expired), avatar dibiarkan null -> tampil avatar generate.
            val resolvedAvatarUrl = if (isPremium) profile?.avatarUrl else null

            // Simpan status premium diri sendiri ke cache juga, biar bubble
            // pesan sendiri (kalau suatu saat ditampilin ke user lain) konsisten
            // dan gak perlu ngecek ulang lewat checkPremiumForNewSenders().
            checkedUids += firebaseUid
            premiumStatusCache[firebaseUid] = isPremium

            _uiState.value = _uiState.value.copy(
                displayUsername = profile?.username?.ifBlank { fallbackUsername } ?: fallbackUsername,
                displayAvatarUrl = resolvedAvatarUrl,
                isPremium = isPremium,
                premiumUids = premiumUidsSnapshot()
            )
        }
    }

    private fun premiumUidsSnapshot(): Set<String> =
        premiumStatusCache.filterValues { it }.keys.toSet()

    /**
     * Cek status premium buat pengirim-pengirim baru yang muncul di daftar
     * pesan (belum pernah dicek sebelumnya di sesi ini), lalu update
     * `premiumUids` di uiState biar badge Premium muncul di samping
     * username mereka. Dijalankan tiap habis refreshMessages().
     */
    private fun checkPremiumForNewSenders(messages: List<ChatMessage>) {
        val newUids = messages
            .map { it.firebaseUid }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { checkedUids.contains(it) }
        if (newUids.isEmpty()) return

        checkedUids += newUids
        viewModelScope.launch {
            newUids.forEach { uid ->
                val result = premiumRepository.checkPremiumStatus(uid)
                premiumStatusCache[uid] = result.getOrNull()?.isPremium ?: false
            }
            _uiState.value = _uiState.value.copy(premiumUids = premiumUidsSnapshot())
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
            checkPremiumForNewSenders(messages)
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
                    message = safeText,
                    replyToId = state.replyTarget?.id,
                    replyToUsername = state.replyTarget?.username,
                    replyToMessage = state.replyTarget?.message
                )
                _uiState.value = _uiState.value.copy(isSending = false, replyTarget = null)
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

    // --- Reply ---

    fun setReplyTarget(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(replyTarget = message)
    }

    fun clearReplyTarget() {
        _uiState.value = _uiState.value.copy(replyTarget = null)
    }

    // --- Hapus pesan ---

    /** Cuma bisa hapus pesan sendiri -- dicek dua kali (UI cuma nampilin tombol di pesan sendiri, dan di sini juga). */
    fun deleteMessage(message: ChatMessage) {
        if (message.firebaseUid != firebaseUid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingMessageId = message.id, errorMessage = null)
            try {
                repository.deleteMessage(id = message.id, firebaseUid = firebaseUid)
                _uiState.value = _uiState.value.copy(
                    deletingMessageId = null,
                    messages = _uiState.value.messages.filterNot { it.id == message.id }
                )
                if (_uiState.value.replyTarget?.id == message.id) {
                    clearReplyTarget()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    deletingMessageId = null,
                    errorMessage = e.localizedMessage ?: "Gagal menghapus pesan"
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
