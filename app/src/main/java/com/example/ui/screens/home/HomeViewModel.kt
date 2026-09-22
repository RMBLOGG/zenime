package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RemoteConfigManager
import com.example.data.common.Result
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.BacakomikListItem
import com.example.data.model.Clan
import com.example.data.model.CuplixItem
import com.example.data.model.ManraItem
import com.example.data.model.HomeResponse
import com.example.data.model.TopSupporter
import com.example.data.model.UserXpDisplay
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ClanRepository
import com.example.data.repository.CoinRepository
import com.example.data.repository.ComicRepository
import com.example.data.repository.PremiumRepository
import com.example.data.repository.SupportRepository
import com.example.data.repository.XpRepository
import com.example.ui.screens.cuplix.CuplixViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * State buat kartu profil ala AniBiPlay di paling atas Beranda -- avatar,
 * username, zenime_code (pengganti "#id" di referensi), sisa hari Premium
 * (pengganti "Level"), dan saldo ZCoin (pengganti "Crystal").
 */
data class HomeProfileUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val avatarUrl: String? = null,
    val zenimeCode: String? = null,
    val userNumber: Long? = null,
    val isPremium: Boolean = false,
    val premiumDaysLeft: Long? = null,
    val coinBalance: Long = 0L,
    val level: Int = 1,
    val clanTag: String? = null
)

class HomeViewModel(
    private val repository: AnimeRepository,
    private val comicRepository: ComicRepository,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val coinRepository: CoinRepository = CoinRepository(),
    private val xpRepository: XpRepository = XpRepository(),
    private val clanRepository: ClanRepository = ClanRepository(),
    private val supportRepository: SupportRepository = SupportRepository(),
    private val firebaseUid: String? = null
) : ViewModel() {

    private val _homeState = MutableStateFlow<Result<HomeResponse>>(Result.Loading)
    val homeState: StateFlow<Result<HomeResponse>> = _homeState.asStateFlow()

    // Section "Komik Terbaru" di Beranda -- diambil terpisah dari homeState
    // anime supaya gagal/lambatnya salah satu gak saling block yang lain.
    private val _comicLatestState = MutableStateFlow<Result<List<BacakomikListItem>>>(Result.Loading)
    val comicLatestState: StateFlow<Result<List<BacakomikListItem>>> = _comicLatestState.asStateFlow()

    // Kustomisasi Hero Banner Carousel -- dibaca dari Pengaturan, live-update
    // (bukan cuma dibaca sekali pas init) berkat StateFlow, jadi begitu user
    // ganti gaya/kecepatan di Pengaturan, Beranda langsung ke-refresh tanpa
    // perlu buka-tutup app.
    val heroStyle: StateFlow<String> = repository.userPrefs.heroStyleFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = "FULL_BLEED")

    val heroAutoplay: StateFlow<Boolean> = repository.userPrefs.heroAutoplayFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    val heroIntervalMs: StateFlow<Int> = repository.userPrefs.heroIntervalMsFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 4500)

    val heroItemCount: StateFlow<Int> = repository.userPrefs.heroItemCountFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 6)

    val heroSource: StateFlow<String> = repository.userPrefs.heroSourceFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = "AUTO")

    // Dipakai buat nampilin video hasil download di Beranda pas homeState
    // gagal (biasanya lagi offline) -- daftar ini dari Room lokal, gak
    // butuh internet sama sekali buat kebaca.
    val downloads: StateFlow<List<DownloadedEpisodeEntity>> = repository.allDownloads
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // "Terakhir Ditonton" -- riwayat tonton lokal, dipakai buat row continue
    // watching di paling atas Beranda (persis posisinya di referensi AniBiPlay).
    val continueWatching: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // Strip "Cuplix" di Beranda (di bawah "Dukung Kami"). Diambil terpisah dan
    // diam-diam gagal: kalau error/kosong, section-nya tinggal tidak tampil.
    private val _cuplixClips = MutableStateFlow<List<CuplixItem>>(emptyList())
    val cuplixClips: StateFlow<List<CuplixItem>> = _cuplixClips.asStateFlow()

    // Section "Baca Manra" (data/manra/list). Gagal/kosong -> section tidak tampil.
    private val _manraItems = MutableStateFlow<List<ManraItem>>(emptyList())
    val manraItems: StateFlow<List<ManraItem>> = _manraItems.asStateFlow()

    private val _profileState = MutableStateFlow(HomeProfileUiState())
    val profileState: StateFlow<HomeProfileUiState> = _profileState.asStateFlow()

    // Data ringkas buat slide "Top Leaderboard" di dalem Hero Carousel --
    // top 4 XP nonton & top 4 Clan, biar bisa di-swipe langsung dari hero
    // tanpa user perlu ke Pengaturan atau buka halaman leaderboard dulu.
    private val _heroLeaderboard = MutableStateFlow(HeroLeaderboardUiState())
    val heroLeaderboard: StateFlow<HeroLeaderboardUiState> = _heroLeaderboard.asStateFlow()

    init {
        loadHome()
        loadComicLatest()
        loadCuplix()
        loadManra()
        loadProfileHeader()
        loadHeroLeaderboard()
        prefetchChat()
    }

    /**
     * Prefetch pesan Chat Global ke cache sesi biar pas user buka Chat,
     * pesan langsung tampil tanpa nunggu spinner. Diam-diam gagal.
     */
    private fun prefetchChat() {
        if (firebaseUid == null || com.example.ui.screens.chat.ChatSessionCache.messages.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val messages = chatRepository.getMessages()
                if (com.example.ui.screens.chat.ChatSessionCache.messages.isEmpty()) {
                    com.example.ui.screens.chat.ChatSessionCache.messages = messages
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Narik data buat kartu profil atas: profil chat (username/avatar),
     * status Premium (buat hitung sisa hari, gantiin "Level" di referensi),
     * zenime_code (gantiin "#id"), & saldo ZCoin (gantiin "Crystal").
     * Kalau lagi belum login (firebaseUid null) langsung skip, biarin
     * default state kosong.
     */
    private fun loadProfileHeader() {
        val uid = firebaseUid ?: return
        viewModelScope.launch {
            _profileState.value = _profileState.value.copy(isLoading = true)

            // 6 data ini GAK saling butuh satu sama lain, tapi sebelumnya
            // ditarik satu-satu (nunggu bergantian) -- jadi total waktu
            // tunggunya kejumlah dari 6 request, bukan cuma nunggu yang paling
            // lama. Ini penyebab utama kartu profil di atas Beranda lama
            // muncul. Sekarang jalan BARENGAN (async + await), total waktunya
            // jadi cuma sepanjang request yang paling lambat di antara mereka.
            val chatProfileDeferred = async { runCatching { chatRepository.getProfile(uid) }.getOrNull() }
            val premiumDeferred = async { premiumRepository.checkPremiumStatus(uid) }
            val identityDeferred = async { premiumRepository.getProfileIdentity(uid) }
            val balanceDeferred = async { coinRepository.getBalance(uid) }
            val myXpDeferred = async { xpRepository.getMyXp(uid) }
            val clanTagDeferred = async { clanRepository.getClanTagsForUids(listOf(uid)) }

            val chatProfile = chatProfileDeferred.await()
            val premiumResult = premiumDeferred.await()
            val premiumStatus = premiumResult.getOrNull()
            val daysLeft = premiumStatus?.expiresAt?.let { computeDaysLeft(it) }

            val identityResult = identityDeferred.await().getOrNull()
            val balanceResult = balanceDeferred.await()
            val myXp = myXpDeferred.await().getOrNull()
            val clanTag = clanTagDeferred.await().getOrNull()?.get(uid)

            _profileState.value = _profileState.value.copy(
                isLoading = false,
                username = chatProfile?.username?.ifBlank { "Pengguna Zenime" } ?: "Pengguna Zenime",
                // Foto profil sekarang bebas buat semua user (bukan lagi benefit
                // Premium) -- selalu dipasang kalau ada, sama kayak di ChatViewModel.
                avatarUrl = chatProfile?.avatarUrl,
                zenimeCode = identityResult?.first,
                userNumber = identityResult?.second,
                isPremium = premiumStatus?.isPremium ?: false,
                premiumDaysLeft = daysLeft,
                coinBalance = balanceResult.getOrNull() ?: 0L,
                level = myXp?.level ?: 1,
                clanTag = clanTag
            )
        }
    }

    private fun loadManra() {
        viewModelScope.launch {
            // Halaman list Manra diperkirakan mulai dari 1; kalau kosong coba 0.
            var result = repository.getManraPage(page = 1)
            if (result is Result.Success && result.data.items.isEmpty()) {
                val alt = repository.getManraPage(page = 0)
                if (alt is Result.Success && alt.data.items.isNotEmpty()) result = alt
            }
            if (result is Result.Success) {
                _manraItems.value = result.data.items.take(12)
            }
        }
    }

    private fun loadCuplix() {
        viewModelScope.launch {
            val result = repository.getCuplixPage(
                sort = CuplixViewModel.SORT_POPULAR,
                seenIds = emptyList(),
                cursors = emptyMap()
            )
            if (result is Result.Success) {
                _cuplixClips.value = result.data.items.take(12)
            }
        }
    }

    fun loadComicLatest() {
        viewModelScope.launch {
            comicRepository.getLatest(page = 1).collect { res ->
                _comicLatestState.value = when (res) {
                    is Result.Success -> Result.Success(res.data.komikList ?: emptyList())
                    is Result.Loading -> Result.Loading
                    is Result.Error -> res
                }
            }
        }
    }

    /** Dipakai dari kartu download di Beranda (fallback pas offline). */
    fun deleteDownload(episodeId: String) {
        viewModelScope.launch {
            repository.deleteEpisodeDownload(episodeId)
        }
    }

    /**
     * @param forceConfigRefresh true kalau ini dipanggil dari tombol
     * "Coba Lagi" manual -- maksa Remote Config fetch ulang dulu (motong
     * cache 1 jam) sebelum narik data, siapa tau base URL barusan
     * diperbaiki/diisi lagi oleh admin di Firebase Console.
     */
    fun loadHome(forceConfigRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceConfigRefresh) {
                RemoteConfigManager.forceRefresh()
            }
            repository.getHome().collect { result ->
                _homeState.value = result
            }
        }
    }

    /** Narik top 4 XP, top 4 Clan, & top 3 Support buat slide-slide leaderboard di Hero Carousel.
     * Public -- dipanggil ulang dari pull-to-refresh & pas Beranda balik ke foreground (ON_RESUME),
     * soalnya XP/Clan/Support bisa berubah dari aksi user lain, bukan cuma aksi kita sendiri. */
    fun loadHeroLeaderboard() {
        viewModelScope.launch {
            // Sama kayak loadProfileHeader() di atas: 3 sumber data ini gak
            // saling butuh, jadi ditarik BARENGAN, bukan satu-satu.
            val topXpDeferred = async { xpRepository.getLeaderboardDisplay() }
            val topClansDeferred = async { clanRepository.browseClans() }
            val topSupportDeferred = async { supportRepository.getTopSupporters() }

            val topXp = topXpDeferred.await()
                .getOrNull()
                ?.sortedByDescending { it.totalXp }
                ?.take(4)
                ?: emptyList()

            val topClans = topClansDeferred.await()
                .getOrNull()
                ?.sortedWith(compareByDescending<Clan> { it.level }.thenByDescending { it.totalXp })
                ?.take(4)
                ?: emptyList()

            val topSupport = topSupportDeferred.await()
                .getOrNull()
                ?.sortedByDescending { it.totalAmount }
                ?.take(3)
                ?: emptyList()

            _heroLeaderboard.value = HeroLeaderboardUiState(
                isLoading = false,
                topXp = topXp,
                topClans = topClans,
                topSupport = topSupport
            )
        }
    }
}

/** State buat slide "Top Leaderboard" (Top XP + Top Clan) & "Top Support" di Hero Carousel. */
data class HeroLeaderboardUiState(
    val isLoading: Boolean = true,
    val topXp: List<UserXpDisplay> = emptyList(),
    val topClans: List<Clan> = emptyList(),
    val topSupport: List<TopSupporter> = emptyList()
)

/** Sisa hari dari expires_at ISO string; null kalau formatnya gak valid. */
private fun computeDaysLeft(expiresAtIso: String): Long? {
    return try {
        val expiresAt = java.time.Instant.parse(expiresAtIso)
        val now = java.time.Instant.now()
        java.time.Duration.between(now, expiresAt).toDays().coerceAtLeast(0)
    } catch (e: Exception) {
        null
    }
}
