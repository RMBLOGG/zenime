package com.example.ui.screens.xp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserXpDisplay
import com.example.data.repository.XpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class XpLeaderboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<UserXpDisplay> = emptyList()
)

class XpLeaderboardViewModel(
    private val repository: XpRepository = XpRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(XpLeaderboardUiState())
    val uiState: StateFlow<XpLeaderboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getLeaderboardDisplay()
                .onSuccess { entries ->
                    _uiState.value = _uiState.value.copy(isLoading = false, entries = entries)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Gagal ambil leaderboard"
                    )
                }
        }
    }

    fun retry() = load()
}
