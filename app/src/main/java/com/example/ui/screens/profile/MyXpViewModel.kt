package com.example.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserXp
import com.example.data.repository.XpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyXpUiState(
    val isLoading: Boolean = true,
    val userXp: UserXp? = null
)

/**
 * Cuma buat baca XP/level SENDIRI (ditampilin progress bar di Profil) --
 * ringan, terpisah dari [ProfileViewModel] biar gak perlu ubah state besar
 * yang udah ada di situ. Gagal load dianggap "belum pernah dapet XP" (0/level 1),
 * bukan error yang perlu ditampilin -- nonton 1x aja bakal muncul otomatis.
 */
class MyXpViewModel(
    private val firebaseUid: String,
    private val repository: XpRepository = XpRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyXpUiState())
    val uiState: StateFlow<MyXpUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repository.getMyXp(firebaseUid)
                .onSuccess { userXp ->
                    _uiState.value = MyXpUiState(isLoading = false, userXp = userXp)
                }
                .onFailure {
                    _uiState.value = MyXpUiState(isLoading = false, userXp = null)
                }
        }
    }
}
