package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RemoteConfigManager
import com.example.data.common.Result
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.BacakomikListItem
import com.example.data.model.HomeResponse
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CoinRepository
import com.example.data.repository.ComicRepository
import com.example.data.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    val isPremium: Boolean = false,
    val premiumDaysLeft: Long? = null,
    val coinBalance: Long = 0L
)

class HomeViewModel(
    private val repository: AnimeRepository,
    private val comicRepository: ComicRepository,
    private val chatRepository: ChatRepository = ChatRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val coinRepository: CoinRepository = CoinRepository(),
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

    private val _profileState = MutableStateFlow(HomeProfileUiState())
    val profileState: StateFlow<HomeProfileUiState> = _profileState.asStateFlow()

    init {
        loadHome()
        loadComicLatest()
        loadProfileHeader()
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

            val chatProfile = try {
                chatRepository.getProfile(uid)
            } catch (e: Exception) {
                null
            }

            val premiumResult = premiumRepository.checkPremiumStatus(uid)
            val premiumStatus = premiumResult.getOrNull()
            val daysLeft = premiumStatus?.expiresAt?.let { computeDaysLeft(it) }

            val codeResult = premiumRepository.getZenimeCode(uid)
            val balanceResult = coinRepository.getBalance(uid)

            _profileState.value = _profileState.value.copy(
                isLoading = false,
                username = chatProfile?.username?.ifBlank { "Pengguna Zenime" } ?: "Pengguna Zenime",
                avatarUrl = if (premiumStatus?.isPremium == true) chatProfile?.avatarUrl else null,
                zenimeCode = codeResult.getOrNull(),
                isPremium = premiumStatus?.isPremium ?: false,
                premiumDaysLeft = daysLeft,
                coinBalance = balanceResult.getOrNull() ?: 0L
            )
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
}

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
