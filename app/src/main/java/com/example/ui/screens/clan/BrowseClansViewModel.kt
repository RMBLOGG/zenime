package com.example.ui.screens.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Clan
import com.example.data.repository.ClanRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowseClanMode { ALL, LEADERBOARD, MY_CLAN }

data class BrowseClansUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val clans: List<Clan> = emptyList(),
    val mode: BrowseClanMode = BrowseClanMode.ALL,
    val searchQuery: String = "",
    /** Clan tempat user sekarang jadi member (null kalau belum gabung clan manapun). */
    val myClanId: String? = null
) {
    val displayedClans: List<Clan>
        get() {
            if (mode == BrowseClanMode.MY_CLAN) {
                return clans.filter { it.id == myClanId }
            }
            val filtered = if (mode == BrowseClanMode.ALL && searchQuery.isNotBlank()) {
                clans.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.tag.contains(searchQuery, ignoreCase = true)
                }
            } else {
                clans
            }
            return if (mode == BrowseClanMode.LEADERBOARD) {
                filtered.sortedWith(compareByDescending<Clan> { it.level }.thenByDescending { it.totalXp })
            } else {
                filtered.sortedByDescending { it.memberCount }
            }
        }
}

class BrowseClansViewModel(
    private val repository: ClanRepository = ClanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseClansUiState())
    val uiState: StateFlow<BrowseClansUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.browseClans()
                .onSuccess { clans ->
                    _uiState.value = _uiState.value.copy(isLoading = false, clans = clans)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Gagal ambil daftar clan"
                    )
                }
            loadMyMembership()
        }
    }

    /** Cek clan tempat user sekarang jadi member, buat tab "Clan Saya". Gagal diam-diam kalau belum login. */
    private suspend fun loadMyMembership() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.getMyMembership(uid)
            .onSuccess { member ->
                _uiState.value = _uiState.value.copy(myClanId = member?.clanId)
            }
    }

    fun onModeChange(mode: BrowseClanMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun retry() = load()
}
