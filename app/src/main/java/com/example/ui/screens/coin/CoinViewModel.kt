package com.example.ui.screens.coin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CoinPackage
import com.example.data.repository.CoinRepository
import com.example.data.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoinUiState(
    val isLoadingBalance: Boolean = true,
    val balance: Long = 0,
    val balanceError: String? = null,

    val isLoadingPackages: Boolean = true,
    val packages: List<CoinPackage> = emptyList(),
    val packagesError: String? = null,

    val selectedPackage: CoinPackage? = null,

    // Kode akun dipakai bareng sama Premium (satu identitas per user),
    // makanya di-reuse dari PremiumRepository -- gak perlu endpoint baru.
    val isLoadingCode: Boolean = false,
    val zenimeCode: String? = null,
    val codeError: String? = null
)

class CoinViewModel(
    private val repository: CoinRepository,
    private val premiumRepository: PremiumRepository,
    private val firebaseUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoinUiState())
    val uiState: StateFlow<CoinUiState> = _uiState.asStateFlow()

    init {
        loadBalance()
        loadPackages()
    }

    fun loadBalance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBalance = true, balanceError = null)
            repository.getBalance(firebaseUid)
                .onSuccess { balance ->
                    _uiState.value = _uiState.value.copy(isLoadingBalance = false, balance = balance)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingBalance = false,
                        balanceError = e.message ?: "Gagal memuat saldo ZCoin"
                    )
                }
        }
    }

    private fun loadPackages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPackages = true, packagesError = null)
            repository.getPackages()
                .onSuccess { packages ->
                    _uiState.value = _uiState.value.copy(isLoadingPackages = false, packages = packages)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPackages = false,
                        packagesError = e.message ?: "Gagal memuat daftar paket ZCoin"
                    )
                }
        }
    }

    fun retryLoadPackages() = loadPackages()

    fun onPackageSelected(pkg: CoinPackage) {
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
            premiumRepository.getZenimeCode(firebaseUid)
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
