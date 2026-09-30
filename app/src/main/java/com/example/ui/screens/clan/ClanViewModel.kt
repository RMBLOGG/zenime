package com.example.ui.screens.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Clan
import com.example.data.model.ClanDonationEntry
import com.example.data.model.ClanMemberDisplay
import com.example.data.model.ClanRoles
import com.example.data.repository.AdminRepository
import com.example.data.repository.ClanRepository
import com.example.data.repository.PremiumRepository
import com.example.data.repository.XpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class ClanTab { MEMBERS, DONATION_TODAY }

/** Status tombol CTA di header, tergantung relasi user sekarang sama clan ini. */
enum class ClanMembershipCta {
    REQUEST_JOIN,       // belum gabung clan manapun -- tombol "Request Join" aktif
    PENDING,            // udah kirim request, nunggu di-approve leader/officer
    BLOCKED_OTHER_CLAN, // udah gabung clan LAIN, gabisa request ke sini
    IS_MEMBER,          // member biasa clan ini
    IS_OFFICER,         // officer (role "co_leader") clan ini -- munculin tombol "Kelola Clan" (versi terbatas)
    IS_ADMIRAL,         // admiral clan ini -- Kelola Clan + kick + ubah role Officer/Member
    IS_VICE_LEADER,     // vice leader clan ini -- Kelola Clan + kick + ubah role Admiral/Officer/Member
    IS_LEADER           // leader clan ini -- munculin tombol "Kelola Clan" (penuh)
}

data class ClanUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val myUid: String = "",
    val clan: Clan? = null,
    val members: List<ClanMemberDisplay> = emptyList(),
    val memberLevels: Map<String, Int> = emptyMap(),
    // Badge centang di list member -- pola sama kayak Chat Global.
    val premiumUids: Set<String> = emptySet(),
    val rolesByUid: Map<String, String> = emptyMap(),
    val roleBadgeColorsByUid: Map<String, String> = emptyMap(),
    val donationsToday: List<ClanDonationEntry> = emptyList(),
    val selectedTab: ClanTab = ClanTab.MEMBERS,
    val searchQuery: String = "",
    val cta: ClanMembershipCta = ClanMembershipCta.REQUEST_JOIN,
    val isSubmittingJoin: Boolean = false,
    val joinFeedback: String? = null,
    val showDonateDialog: Boolean = false,
    val donateAmountInput: String = "",
    val isDonating: Boolean = false,
    val donateFeedback: String? = null,
    val showLeaveDialog: Boolean = false,
    val isLeaving: Boolean = false,
    val leaveFeedback: String? = null,
    val leftClan: Boolean = false,
    // Aksi role/kick lewat menu titik tiga di list member.
    val actionTarget: ClanMemberDisplay? = null,
    val isActionLoading: Boolean = false,
    val actionFeedback: String? = null
) {
    /** Role user sekarang di clan INI (null kalau bukan member). */
    val myRole: String?
        get() = when (cta) {
            ClanMembershipCta.IS_LEADER -> ClanRoles.LEADER
            ClanMembershipCta.IS_VICE_LEADER -> ClanRoles.VICE_LEADER
            ClanMembershipCta.IS_ADMIRAL -> ClanRoles.ADMIRAL
            ClanMembershipCta.IS_OFFICER -> ClanRoles.OFFICER
            ClanMembershipCta.IS_MEMBER -> ClanRoles.MEMBER
            else -> null
        }

    val canDonate: Boolean
        get() = myRole != null

    /** Leader gabisa "Keluar Clan" biasa -- harus transfer kepemimpinan/bubarkan clan dulu (di luar cakupan tombol ini). */
    val canLeave: Boolean
        get() = myRole != null && myRole != ClanRoles.LEADER

    val leader: ClanMemberDisplay?
        get() = members.find { it.role == ClanRoles.LEADER }

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
    private val repository: ClanRepository = ClanRepository(),
    private val xpRepository: XpRepository = XpRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClanUiState(myUid = firebaseUid))
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

            // Daftar member wajib duluan (donasi & level butuh daftar uid-nya),
            // tapi cek keanggotaan sendiri (buat CTA) gak saling butuh sama
            // member -- jalan BARENGAN, bukan berurutan.
            val membersDeferred = async { repository.getMembers(clanId).getOrDefault(emptyList()) }
            val myMembershipDeferred = async { repository.getMyMembership(firebaseUid).getOrNull() }

            val members = membersDeferred.await()
            val myMembership = myMembershipDeferred.await()
            val cta = resolveCta(members, myMembership)

            // Donasi hari ini & level per member juga gak saling butuh --
            // ditarik BARENGAN. getTodayDonations dikasih tau role member yang
            // UDAH ada di atas, biar dia gak nge-fetch ulang daftar member yang
            // sama (sebelumnya ini request dobel percuma).
            val donationsDeferred = async {
                repository.getTodayDonations(
                    clanId,
                    knownRoleByUid = members.associate { it.firebaseUid to it.role }
                ).getOrDefault(emptyList())
            }
            val levelsDeferred = async {
                if (members.isNotEmpty()) {
                    xpRepository.getLevelsForUids(members.map { it.firebaseUid }).getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }

            // Premium & role (centang) juga ditarik BARENGAN, best-effort:
            // gagal = centang gak muncul, bukan bikin layar error.
            val premiumDeferred = async {
                if (members.isNotEmpty()) {
                    runCatching { premiumRepository.getPremiumStatusForUids(members.map { it.firebaseUid }) }
                        .getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }
            val rolesDeferred = async {
                if (members.isNotEmpty()) {
                    runCatching { adminRepository.getRolesForUids(members.map { it.firebaseUid }) }
                        .getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }
            val roles = rolesDeferred.await()

            // Urutan: leader clan tetap paling atas, lalu member yang punya
            // role global (developer/admin/moderator) walau belum donasi,
            // sisanya ikut urutan asli. sortedWith stabil, jadi urutan
            // relatif di tiap grup gak berubah.
            val sortedMembers = members.sortedWith(
                compareByDescending<ClanMemberDisplay> { ClanRoles.rank(it.role) }
                    .thenByDescending { roles.containsKey(it.firebaseUid) }
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                clan = clan,
                members = sortedMembers,
                memberLevels = levelsDeferred.await(),
                premiumUids = premiumDeferred.await().filterValues { it }.keys.toSet(),
                rolesByUid = roles.mapValues { it.value.role },
                roleBadgeColorsByUid = roles.mapNotNull { (uid, r) -> r.badgeColor?.let { uid to it } }.toMap(),
                donationsToday = donationsDeferred.await(),
                cta = cta
            )
        }
    }

    private suspend fun resolveCta(
        members: List<ClanMemberDisplay>,
        myMembership: com.example.data.model.ClanMember?
    ): ClanMembershipCta {
        return when {
            myMembership == null -> {
                val pending = repository.getMyJoinRequestPending(clanId).getOrDefault(false)
                if (pending) ClanMembershipCta.PENDING else ClanMembershipCta.REQUEST_JOIN
            }
            myMembership.clanId != clanId -> ClanMembershipCta.BLOCKED_OTHER_CLAN
            myMembership.role == "leader" -> ClanMembershipCta.IS_LEADER
            myMembership.role == "vice_leader" -> ClanMembershipCta.IS_VICE_LEADER
            myMembership.role == "admiral" -> ClanMembershipCta.IS_ADMIRAL
            myMembership.role == "co_leader" -> ClanMembershipCta.IS_OFFICER
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

    fun onLeaveDialogToggle(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLeaveDialog = show, leaveFeedback = null)
    }

    fun confirmLeaveClan() {
        if (!_uiState.value.canLeave) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLeaving = true, leaveFeedback = null)
            repository.leaveClan(clanId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLeaving = false,
                        showLeaveDialog = false,
                        leftClan = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLeaving = false,
                        leaveFeedback = e.message ?: "Gagal keluar clan"
                    )
                }
        }
    }

    // --- Aksi role / kick (dari menu titik tiga di list member) ---

    fun onMemberActionClick(member: ClanMemberDisplay) {
        val myRole = _uiState.value.myRole
        if (!ClanRoles.canActOn(myRole, member.role) || member.firebaseUid == firebaseUid) return
        _uiState.value = _uiState.value.copy(actionTarget = member, actionFeedback = null)
    }

    fun dismissMemberAction() {
        if (_uiState.value.isActionLoading) return
        _uiState.value = _uiState.value.copy(actionTarget = null, actionFeedback = null)
    }

    fun changeMemberRole(newRole: String) {
        val state = _uiState.value
        val target = state.actionTarget ?: return
        if (newRole !in ClanRoles.assignableRoles(state.myRole, target.role)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, actionFeedback = null)
            repository.setMemberRoleTo(clanId, target.firebaseUid, newRole)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isActionLoading = false, actionTarget = null)
                    loadAll()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        actionFeedback = e.message ?: "Gagal ubah role member"
                    )
                }
        }
    }

    fun kickMember() {
        val state = _uiState.value
        val target = state.actionTarget ?: return
        if (!ClanRoles.canKick(state.myRole, target.role)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, actionFeedback = null)
            repository.kickMember(clanId, target.firebaseUid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isActionLoading = false, actionTarget = null)
                    loadAll()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        actionFeedback = e.message ?: "Gagal kick member"
                    )
                }
        }
    }

    fun retry() = loadAll()
}
