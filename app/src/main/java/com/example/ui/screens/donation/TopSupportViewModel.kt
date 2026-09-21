package com.example.ui.screens.donation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TopSupporter
import com.example.data.repository.PremiumRepository
import com.example.data.repository.SupportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TopSupportUiState(
    val isLoading: Boolean = true,
    val supporters: List<TopSupporter> = emptyList(),
    val error: String? = null,
    // Kode Zenime user -- disalin pas mau donasi lewat SociaBuzz biar donasinya
    // otomatis nyambung ke akun ini. Null kalau belum login / gagal diambil.
    val zenimeCode: String? = null
)

class TopSupportViewModel(
    private val repository: SupportRepository,
    private val premiumRepository: PremiumRepository,
    private val firebaseUid: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopSupportUiState())
    val uiState: StateFlow<TopSupportUiState> = _uiState.asStateFlow()

    init {
        loadTopSupporters()
        loadZenimeCode()
    }

    fun loadTopSupporters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getTopSupporters()
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, supporters = list)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Gagal memuat Top Support"
                    )
                }
        }
    }

    private fun loadZenimeCode() {
        val uid = firebaseUid ?: return
        viewModelScope.launch {
            premiumRepository.getZenimeCode(uid)
                .onSuccess { code ->
                    _uiState.value = _uiState.value.copy(zenimeCode = code)
                }
            // Gagal ambil kode -> diam aja; donasi tetap jalan, cuma gak otomatis nyambung ke akun.
        }
    }
}
