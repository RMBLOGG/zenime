package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val COOLDOWN_SECONDS = 5
private const val POLL_INTERVAL_MS = 3000L
private const val MAX_MESSAGE_LENGTH = 300

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val errorMessage: String? = null
)

/**
 * Cooldown 5 detik dihitung MURNI di client (gak ada tabel/kolom rate-limit
 * di server) -- cukup buat nyegah spam kasual dari UI normal. Kalau nanti
 * mau lebih ketat (misal cegah orang yang modif APK/panggil API langsung),
 * itu butuh pengecekan tambahan di server (contoh: Edge Function yang cek
 * timestamp pesan terakhir per firebase_uid sebelum insert).
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val firebaseUid: String,
    private val username: String,
    private val avatarUrl: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        startPolling()
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
            try {
                repository.sendMessage(
                    firebaseUid = firebaseUid,
                    username = username,
                    avatarUrl = avatarUrl,
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

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        cooldownJob?.cancel()
    }
}
