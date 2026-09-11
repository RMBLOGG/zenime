package com.example.ui.screens.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Clan
import com.example.data.model.ClanDonationEntry
import com.example.data.model.ClanMemberDisplay
import com.example.data.repository.ClanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ClanTab { MEMBERS, DONATION_TODAY }

/** Status tombol CTA di header, tergantung relasi user sekarang sama clan ini. */
enum class ClanMembershipCta {
    REQUEST_JOIN,       // belum gabung clan manapun -- tombol "Request Join" aktif
    PENDING,            // udah kirim request, nunggu di-approve leader/co-leader
    BLOCKED_OTHER_CLAN, // udah gabung clan LAIN, gabisa request ke sini
    IS_MEMBER,          // member clan ini (non-leader)
    IS_LEADER           // leader clan ini -- munculin tombol "Kelola Clan"
}

data class ClanUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val clan: Clan? = null,
    val members: List<ClanMemberDisplay> = emptyList(),
    val donationsToday: List<ClanDonationEntry> = emptyList(),
    val selectedTab: ClanTab = ClanTab.MEMBERS,
    val searchQuery: String = "",
    val cta: ClanMembershipCta = ClanMembershipCta.REQUEST_JOIN,
    val isSubmittingJoin: Boolean = false,
    val joinFeedback: String? = null,
    val showDonateDialog: Boolean = false,
    val donateAmountInput: String = "",
    val isDonating: Boolean = false,
    val donateFeedback: String? = null
) {
    val canDonate: Boolean
        get() = cta == ClanMembershipCta.IS_MEMBER || cta == ClanMembershipCta.IS_LEADER

    val leader: ClanMemberDisplay?
        get() = members.find { it.role == "leader" }

    val filteredMembers: List<ClanMemberDisplay>
        get() = if (searchQuery.isBlank()) {
            members
        } else {
            members.filter {
                it.username.contains(searchQuery, ignoreCase = true) ||
                    it.firebaseUid.contains(searchQuery, ignoreCase = true)
            }
        }

    val totalDonatedToday: Long get() = donationsToday.sumOf { it.amountToday }
    val donorCountToday: Int get() = donationsToday.size
}

class ClanViewModel(
    private val clanId: String,
    private val firebaseUid: String,
    private val repository: ClanRepository = ClanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClanUiState())
    val uiState: StateFlow<ClanUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val clanResult = repository.getClan(clanId)
            val clan = clanResult.getOrNull()
            if (clan == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = clanResult.exceptionOrNull()?.message ?: "Clan tidak ditemukan"
                )
                return@launch
            }

            val members = repository.getMembers(clanId).getOrDefault(emptyList())
            val donations = repository.getTodayDonations(clanId).getOrDefault(emptyList())
            val cta = resolveCta(members)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                clan = clan,
                members = members,
                donationsToday = donations,
                cta = cta
            )
        }
    }

    private suspend fun resolveCta(members: List<ClanMemberDisplay>): ClanMembershipCta {
        val myMembership = repository.getMyMembership(firebaseUid).getOrNull()
        return when {
            myMembership == null -> {
                val pending = repository.getMyJoinRequestPending(clanId).getOrDefault(false)
                if (pending) ClanMembershipCta.PENDING else ClanMembershipCta.REQUEST_JOIN
            }
            myMembership.clanId != clanId -> ClanMembershipCta.BLOCKED_OTHER_CLAN
            myMembership.role == "leader" -> ClanMembershipCta.IS_LEADER
            else -> ClanMembershipCta.IS_MEMBER
        }
    }

    fun onTabSelected(tab: ClanTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun requestJoin() {
        if (_uiState.value.cta != ClanMembershipCta.REQUEST_JOIN) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingJoin = true, joinFeedback = null)
            repository.submitJoinRequest(clanId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingJoin = false,
                        cta = ClanMembershipCta.PENDING,
                        joinFeedback = "Request join terkirim, tunggu di-approve leader/co-leader ya"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingJoin = false,
                        joinFeedback = e.message ?: "Gagal mengirim request join"
                    )
                }
        }
    }

    fun clearJoinFeedback() {
        _uiState.value = _uiState.value.copy(joinFeedback = null)
    }

    fun onDonateDialogToggle(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showDonateDialog = show,
            donateAmountInput = if (show) "" else _uiState.value.donateAmountInput,
            donateFeedback = null
        )
    }

    fun onDonateAmountChange(value: String) {
        _uiState.value = _uiState.value.copy(donateAmountInput = value.filter { it.isDigit() }.take(9))
    }

    fun submitDonation() {
        val amount = _uiState.value.donateAmountInput.toLongOrNull()
        if (amount == null || amount <= 0) {
            _uiState.value = _uiState.value.copy(donateFeedback = "Masukin jumlah ZCoin yang valid")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDonating = true, donateFeedback = null)
            repository.donateToClan(clanId, amount)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isDonating = false,
                        showDonateDialog = false,
                        donateAmountInput = ""
                    )
                    loadAll() // refresh level/XP/kontribusi/daftar donasi hari ini
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isDonating = false,
                        donateFeedback = e.message ?: "Gagal donasi"
                    )
                }
        }
    }

    fun retry() = loadAll()
}
