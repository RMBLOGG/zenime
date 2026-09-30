package com.example.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FriendDisplay
import com.example.data.repository.FriendRepository
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val friends: List<FriendDisplay> = emptyList(),
    val incoming: List<FriendDisplay> = emptyList(),
    val outgoing: List<FriendDisplay> = emptyList(),
    /** friendshipId yang lagi diproses (buat disable tombolnya). */
    val busyId: String? = null,
    val actionError: String? = null
)

class FriendsViewModel(
    private val myUid: String,
    private val repository: FriendRepository = FriendRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getLists(myUid)
                .onSuccess { lists ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        friends = lists.friends,
                        incoming = lists.incoming,
                        outgoing = lists.outgoing
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyErrorMessage(e, "Gagal memuat daftar teman")
                    )
                }
        }
    }

    fun accept(item: FriendDisplay) = act(item) { repository.accept(item.friendshipId) }

    /** Tolak permintaan masuk / batalin permintaan keluar / hapus teman. */
    fun remove(item: FriendDisplay) = act(item) { repository.remove(item.friendshipId) }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    private fun act(item: FriendDisplay, block: suspend () -> Result<Unit>) {
        if (_uiState.value.busyId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyId = item.friendshipId, actionError = null)
            val result = block()
            val lists = repository.getLists(myUid).getOrNull()
            _uiState.value = _uiState.value.copy(
                busyId = null,
                friends = lists?.friends ?: _uiState.value.friends,
                incoming = lists?.incoming ?: _uiState.value.incoming,
                outgoing = lists?.outgoing ?: _uiState.value.outgoing,
                actionError = result.exceptionOrNull()?.let { friendlyErrorMessage(it, "Aksi gagal, coba lagi") }
            )
        }
    }
}
