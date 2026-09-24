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
import kotlinx.coroutines.async
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
    val actionInFlightId: String? = null, // request_id atau firebase_uid yang lagi diproses
    val myFirebaseUid: String = ""
) {
    /** Role viewer sekarang di clan ini. Cuma leader yang boleh liat tab Settings & atur role/kick. */
    val myRole: String
        get() = members.find { it.firebaseUid == myFirebaseUid }?.role ?: "member"

    val isLeader: Boolean get() = myRole == "leader"
}

class ManageClanViewModel(
    private val clanId: String,
    private val myFirebaseUid: String,
    private val repository: ClanRepository = ClanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageClanUiState(myFirebaseUid = myFirebaseUid))
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

            // Member & pending request gak saling butuh -- ditarik BARENGAN.
            val membersDeferred = async { repository.getMembers(clanId).getOrDefault(emptyList()) }
            val pendingDeferred = async { repository.getPendingJoinRequests(clanId).getOrDefault(emptyList()) }
            val members = membersDeferred.await()
            val pending = pendingDeferred.await()

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

    /**
     * Approve/reject SATU request. Sebelumnya sukses -> panggil loadAll()
     * lagi (reload semua: clan + member + SEMUA pending request dari nol,
     * lewat jalur yang tadinya lambat) -- padahal cukup buang baris yang
     * baru diproses dari daftar lokal. Sekarang: update state LANGSUNG,
     * tanpa nunggu round-trip lagi, jadi kelihatan instan.
     */
    fun respondToRequest(requestId: String, approve: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlightId = requestId)
            repository.respondJoinRequest(requestId, approve)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        pendingRequests = _uiState.value.pendingRequests.filterNot { it.requestId == requestId }
                    )
                    // Kalau di-approve, ada member baru -- tarik ulang daftar
                    // member doang DI BELAKANG LAYAR (gak nyalain isLoading,
                    // gak ngeblok UI), biar tab Member ikut update tanpa bikin
                    // user nunggu.
                    if (approve) {
                        val refreshedMembers = repository.getMembers(clanId).getOrNull()
                        if (refreshedMembers != null) {
                            _uiState.value = _uiState.value.copy(members = refreshedMembers)
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        error = e.message ?: "Gagal memproses request"
                    )
                }
        }
    }

    /** Sama kayak respondToRequest() -- update state lokal langsung, gak loadAll() ulang. */
    fun kickMember(targetUid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlightId = targetUid)
            repository.kickMember(clanId, targetUid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        members = _uiState.value.members.filterNot { it.firebaseUid == targetUid }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        error = e.message ?: "Gagal kick member"
                    )
                }
        }
    }

    /** Cuma leader yang boleh manggil ini (dicek juga di Edge Function). */
    fun setMemberRole(targetUid: String, makeOfficer: Boolean) {
        if (!_uiState.value.isLeader) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlightId = targetUid)
            repository.setMemberRole(clanId, targetUid, makeOfficer)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        members = _uiState.value.members.map { member ->
                            if (member.firebaseUid == targetUid) {
                                member.copy(role = if (makeOfficer) "co_leader" else "member")
                            } else member
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionInFlightId = null,
                        error = e.message ?: "Gagal ubah role member"
                    )
                }
        }
    }

    fun retry() = loadAll()
}
