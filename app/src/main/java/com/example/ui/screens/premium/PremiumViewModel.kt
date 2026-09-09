package com.example.ui.screens.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PremiumPackage
import com.example.data.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PremiumUiState(
    val isLoadingPackages: Boolean = true,
    val packages: List<PremiumPackage> = emptyList(),
    val packagesError: String? = null,

    val selectedPackage: PremiumPackage? = null,

    val isLoadingCode: Boolean = false,
    val zenimeCode: String? = null,
    val codeError: String? = null
)

class PremiumViewModel(
    private val repository: PremiumRepository,
    private val firebaseUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        loadPackages()
    }

    private fun loadPackages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPackages = true, packagesError = null)
            repository.getPackages()
                .onSuccess { packages ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPackages = false,
                        packages = packages
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPackages = false,
                        packagesError = e.message ?: "Gagal memuat daftar paket premium"
                    )
                }
        }
    }

    fun retryLoadPackages() = loadPackages()

    /** Dipanggil pas user tap salah satu kartu paket. Sekalian ambil zenime_code kalau belum ada. */
    fun onPackageSelected(pkg: PremiumPackage) {
        _uiState.value = _uiState.value.copy(selectedPackage = pkg)
        if (_uiState.value.zenimeCode == null) {
            loadZenimeCode()
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPackage = null)
    }

    private fun loadZenimeCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCode = true, codeError = null)
            repository.getZenimeCode(firebaseUid)
                .onSuccess { code ->
                    _uiState.value = _uiState.value.copy(isLoadingCode = false, zenimeCode = code)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingCode = false,
                        codeError = e.message ?: "Gagal mengambil kode akun"
                    )
                }
        }
    }

    fun retryLoadCode() = loadZenimeCode()
}
