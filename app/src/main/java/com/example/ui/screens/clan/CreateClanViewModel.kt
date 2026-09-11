package com.example.ui.screens.clan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Clan
import com.example.data.repository.ClanRepository
import com.example.data.repository.CoinRepository
import com.example.util.ClanPhotoUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val CREATE_CLAN_COST = 2500L

data class CreateClanUiState(
    val isLoadingBalance: Boolean = true,
    val coinBalance: Long = 0,
    val name: String = "",
    val tag: String = "",
    val photoUri: Uri? = null,
    val isUploadingPhoto: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val createdClan: Clan? = null
) {
    val canAfford: Boolean get() = coinBalance >= CREATE_CLAN_COST
    val isNameValid: Boolean get() = name.trim().length in 3..30
    val isTagValid: Boolean get() = tag.trim().uppercase().matches(Regex("^[A-Z0-9]{3}$"))
    val canSubmit: Boolean get() = isNameValid && isTagValid && canAfford && !isSubmitting && !isUploadingPhoto
}

class CreateClanViewModel(
    private val firebaseUid: String,
    private val repository: ClanRepository = ClanRepository(),
    private val coinRepository: CoinRepository = CoinRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateClanUiState())
    val uiState: StateFlow<CreateClanUiState> = _uiState.asStateFlow()

    init {
        loadBalance()
    }

    fun loadBalance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBalance = true)
            val balance = coinRepository.getBalance(firebaseUid).getOrDefault(0)
            _uiState.value = _uiState.value.copy(isLoadingBalance = false, coinBalance = balance)
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, error = null)
    }

    fun onTagChange(value: String) {
        // Batesin input maksimal 3 karakter dari sisi UI juga biar user gak
        // ngetik kepanjangan -- validasi format aslinya tetep di isTagValid.
        val trimmed = value.uppercase().take(3)
        _uiState.value = _uiState.value.copy(tag = trimmed, error = null)
    }

    fun onPhotoPicked(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    fun submit(context: Context) {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

            var photoUrl: String? = null
            if (state.photoUri != null) {
                _uiState.value = _uiState.value.copy(isUploadingPhoto = true)
                val uploadResult = runCatching {
                    ClanPhotoUploader.uploadClanPhoto(
                        context = context,
                        imageUri = state.photoUri,
                        pathKey = "$firebaseUid-${System.currentTimeMillis()}"
                    )
                }
                _uiState.value = _uiState.value.copy(isUploadingPhoto = false)

                uploadResult.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = "Gagal upload foto: ${e.message}"
                    )
                    return@launch
                }
                photoUrl = uploadResult.getOrNull()
            }

            repository.createClan(name = state.name.trim(), tag = state.tag.trim(), photoUrl = photoUrl)
                .onSuccess { clan ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, createdClan = clan)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Gagal bikin clan"
                    )
                }
        }
    }
}
