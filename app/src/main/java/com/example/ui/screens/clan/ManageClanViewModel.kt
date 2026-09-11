package com.example.ui.screens.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Clan
import com.example.data.model.ClanMemberDisplay
import com.example.data.model.PendingJoinRequestDisplay
import com.example.data.repository.ClanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ManageClanTab { SETTINGS, REQUESTS, MEMBERS }

data class ManageClanUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val clan: Clan? = null,
    val members: List<ClanMemberDisplay> = emptyList(),
    val pendingRequests: List<PendingJoinRequestDisplay> = emptyList(),
    val selectedTab: ManageClanTab = ManageClanTab.REQUESTS,
    val nameInput: String = "",
    val tagInput: String = "",
    val isSavingSettings: Boolean = false,
    val settingsFeedback: String? = null,
    val actionInFlightId: String? = null // request_id atau firebase_uid yang lagi diproses
)

class ManageClanViewModel(
    private val clanId: String,
    private val repository: ClanRepository = ClanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageClanUiState())
    val uiState: StateFlow<ManageClanUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val clan = repository.getClan(clanId).getOrNull()
            if (clan == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Clan tidak ditemukan")
                return@launch
            }

            val members = repository.getMembers(clanId).getOrDefault(emptyList())
            val pending = repository.getPendingJoinRequests(clanId).getOrDefault(emptyList())

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                clan = clan,
                members = members,
                pendingRequests = pending,
                nameInput = clan.name,
                tagInput = clan.tag
            )
        }
    }

    fun onTabSelected(tab: ManageClanTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun onNameInputChange(value: String) {
        _uiState.value = _uiState.value.copy(nameInput = value, settingsFeedback = null)
    }

    fun onTagInputChange(value: String) {
        _uiState.value = _uiState.value.copy(tagInput = value.uppercase().take(3), settingsFeedback = null)
    }

    fun saveSettings() {
        val state = _uiState.value
        val clan = state.clan ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSettings = true, settingsFeedback = null)

            val newName = state.nameInput.trim().takeIf { it.isNotEmpty() && it != clan.name }
            val newTag = state.tagInput.trim().takeIf { it.isNotEmpty() && it != clan.tag }

            repository.updateClanSettings(clanId = clanId, name = newName, tag = newTag)
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        clan = updated,
                        settingsFeedback = "Settingan clan berhasil disimpan"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        settingsFeedback = e.message ?: "Gagal simpan settingan"
                    )
                }
        }
    }

    fun onPhotoUploaded(url: String) {
        val clan = _uiState.value.clan ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSettings = true)
            repository.updateClanSettings(clanId = clanId, photoUrl = url)
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(isSavingSettings = false, clan = updated)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        settingsFeedback = e.message ?: "Gagal upload foto clan"
                    )
                }
        }
    }

    fun respondToRequest(requestId: String, approve: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlightId = requestId)
            repository.respondJoinRequest(requestId, approve)
                .onSuccess { loadAll() }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        error = e.message ?: "Gagal memproses request"
                    )
                }
        }
    }

    fun kickMember(targetUid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlightId = targetUid)
            repository.kickMember(clanId, targetUid)
                .onSuccess { loadAll() }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        error = e.message ?: "Gagal kick member"
                    )
                }
        }
    }

    fun retry() = loadAll()
}
