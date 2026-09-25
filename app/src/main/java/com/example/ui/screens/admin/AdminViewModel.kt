package com.example.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RoleListEntry
import com.example.data.model.UserListEntry
import com.example.data.model.ZenimeRole
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Tab "Semua User" -- daftar/pencarian semua user, sumber utama aksi
    // role/ban device/ban akun (per baris), gantiin form ketik-UID-manual.
    val userSearchQuery: String = "",
    val isLoadingUserList: Boolean = false,
    val userList: List<UserListEntry> = emptyList(),
    val userListError: String? = null,
    val userListHasMore: Boolean = false,
    val isLoadingMoreUsers: Boolean = false,

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

    private var searchDebounceJob: Job? = null
    private var userListOffset = 0

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
                    if (role != null) {
                        loadUserList(resetQuery = true)
                        // Tab "Pemegang Role" cuma dipakai developer, gak perlu
                        // ditarik buat admin/moderator.
                        if (role == ZenimeRole.DEVELOPER) loadRoleList()
                    }
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

    /** Dipanggil tiap ketikan search berubah -- di-debounce biar gak spam request. */
    fun onUserSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(userSearchQuery = query)
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(350)
            loadUserList(resetQuery = false)
        }
    }

    fun loadUserList(resetQuery: Boolean) {
        if (resetQuery) _uiState.value = _uiState.value.copy(userSearchQuery = "")
        val query = _uiState.value.userSearchQuery
        userListOffset = 0
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingUserList = true, userListError = null)
            repository.listUsers(search = query, offset = 0)
                .onSuccess { (list, hasMore) ->
                    userListOffset = list.size
                    _uiState.value = _uiState.value.copy(
                        isLoadingUserList = false,
                        userList = list,
                        userListHasMore = hasMore
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingUserList = false,
                        userListError = e.message ?: "Gagal memuat daftar user"
                    )
                }
        }
    }

    /** Muat halaman berikutnya (dipanggil pas nyampe bawah list), nambahin ke list yang udah ada. */
    fun loadMoreUsers() {
        val state = _uiState.value
        if (state.isLoadingMoreUsers || !state.userListHasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreUsers = true)
            repository.listUsers(search = state.userSearchQuery, offset = userListOffset)
                .onSuccess { (list, hasMore) ->
                    userListOffset += list.size
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreUsers = false,
                        userList = _uiState.value.userList + list,
                        userListHasMore = hasMore
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMoreUsers = false)
                }
        }
    }

    /** Refresh ringan setelah aksi role/ban -- biar badge & status di baris user langsung update. */
    private fun refreshAfterAction() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.listUsers(search = state.userSearchQuery, offset = 0)
                .onSuccess { (list, hasMore) ->
                    userListOffset = list.size
                    _uiState.value = _uiState.value.copy(userList = list, userListHasMore = hasMore)
                }
        }
        if (state.myRole == ZenimeRole.DEVELOPER) loadRoleList()
    }

    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(actionError = null, actionSuccessMessage = null)
    }

    /** Kasih/ganti role user lain. */
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
                    refreshAfterAction()
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
                    refreshAfterAction()
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
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Akun diban")
                    refreshAfterAction()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal ban akun") }
        }
    }

    fun unbanUser(targetUid: String) {
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.unbanUser(targetUid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Ban dicabut")
                    refreshAfterAction()
                }
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
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Device diban")
                    refreshAfterAction()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal ban device") }
        }
    }

    fun unbanDevice(deviceId: String) {
        clearMessages()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            repository.unbanDevice(deviceId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isProcessing = false, actionSuccessMessage = "Ban device dicabut")
                    refreshAfterAction()
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isProcessing = false, actionError = e.message ?: "Gagal cabut ban device") }
        }
    }

    fun clearActionMessages() = clearMessages()

    fun currentUid(): String = myUid
}
