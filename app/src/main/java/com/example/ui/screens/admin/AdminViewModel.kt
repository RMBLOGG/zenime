package com.example.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RoleListEntry
import com.example.data.model.ZenimeRole
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val myRole: ZenimeRole? = null,
    val isLoadingMyRole: Boolean = true,

    val isLoadingRoleList: Boolean = false,
    val roleList: List<RoleListEntry> = emptyList(),
    val roleListError: String? = null,

    val isProcessing: Boolean = false,
    val actionError: String? = null,
    val actionSuccessMessage: String? = null
)

class AdminViewModel(
    private val repository: AdminRepository,
    private val myUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadMyRole()
    }

    private fun loadMyRole() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMyRole = true)
            repository.getMyRole()
                .onSuccess { info ->
                    val role = ZenimeRole.fromValue(info.role)
                    _uiState.value = _uiState.value.copy(myRole = role, isLoadingMyRole = false)
                    // Developer langsung ditarik daftar role -- role lain
                    // (admin/moderator) gak butuh, tab-nya beda per role,
                    // dimuat lazy dari screen pas tab dibuka.
                    if (role == ZenimeRole.DEVELOPER) loadRoleList()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(myRole = null, isLoadingMyRole = false)
                }
        }
    }

    fun loadRoleList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRoleList = true, roleListError = null)
            repository.listRoles()
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoadingRoleList = false, roleList = list)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoleList = false,
                        roleListError = e.message ?: "Gagal memuat daftar role"
                    )
                }
        }
    }

    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(actionError = null, actionSuccessMessage = null)
    }

    /** Kasih/ganti role user lain lewat kode/uid yang diketik developer. */
    fun assignRole(targetUid: String, role: ZenimeRole, badgeColorHex: String?) {
        if (targetUid == myUid) {
            _uiState.value = _uiState.value.copy(actionError = "Gak bisa ganti role diri sendiri")
            return
        }
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.setRole(targetUid, role, badgeColorHex)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Role berhasil diset")
                    loadRoleList()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal set role")
                }
        }
    }

    fun removeRole(targetUid: String) {
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.removeRole(targetUid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Role dicabut")
                    loadRoleList()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal cabut role")
                }
        }
    }

    /** Moderator/developer: ban akun. */
    fun banUser(targetUid: String, reason: String?) {
        if (targetUid == myUid) {
            _uiState.value = _uiState.value.copy(actionError = "Gak bisa ban diri sendiri")
            return
        }
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.banUser(targetUid, reason)
                .onSuccess { _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Akun diban") }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal ban akun") }
        }
    }

    fun unbanUser(targetUid: String) {
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.unbanUser(targetUid)
                .onSuccess { _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Ban dicabut") }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal cabut ban") }
        }
    }

    /** Developer: ban device dari uid target. */
    fun banDevice(targetUid: String, reason: String?) {
        if (targetUid == myUid) {
            _uiState.value = _uiState.value.copy(actionError = "Gak bisa ban device sendiri")
            return
        }
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.banDevice(targetUid, reason)
                .onSuccess { _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Device diban") }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal ban device") }
        }
    }

    fun clearActionMessages() = clearMessages()
}
