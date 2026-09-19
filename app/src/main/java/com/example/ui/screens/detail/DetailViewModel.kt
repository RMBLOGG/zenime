package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.AnimeItem
import com.example.data.model.CuplixItem
import com.example.data.model.GalleryImage
import com.example.data.model.GalleryKind
import com.example.data.model.EpisodeItem
import com.example.data.model.StreamServer
import com.example.data.repository.AnimeRepository
import com.example.data.repository.PremiumRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tab di halaman detail. EPISODE = isi lama (genre, sinopsis, daftar episode). */
enum class DetailTab(val label: String) {
    EPISODE("Episode"),
    SEASON("Season"),
    CUPLIX("Cuplix"),
    COVER("Cover"),
    POSTER("Poster")
}

/** State satu tab berisi daftar berhalaman (season, cuplix, cover, poster). */
data class PagedTabState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,      // muat awal
    val isLoadingMore: Boolean = false,  // muat halaman berikutnya
    val error: String? = null,
    val hasMore: Boolean = true,
    val loaded: Boolean = false          // sudah pernah berhasil dimuat
)

class DetailViewModel(
    private val repository: AnimeRepository,
    val animeId: String,
    private val firebaseUid: String? = null
) : ViewModel() {

    // Dipakai buat nge-lock episode di luar trial gratis (lihat
    // isEpisodeLocked & EpisodeHorizontalCard). Default false -- aman-nya
    // anggap non-premium sampai kebukti sebaliknya.
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Link video buat preview auto-play di hero section (ambil dari
    // episode 1, bukan trailer resmi -- API upstream gak nyediain trailer
    // beneran). Null selama belum siap / gagal / anime gak punya episode.
    private val _previewUrl = MutableStateFlow<String?>(null)
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()

    private val _detailState = MutableStateFlow<Result<AnimeItem>>(Result.Loading)
    val detailState: StateFlow<Result<AnimeItem>> = _detailState.asStateFlow()

    private val _episodesState = MutableStateFlow<Result<List<EpisodeItem>>>(Result.Loading)
    val episodesState: StateFlow<Result<List<EpisodeItem>>> = _episodesState.asStateFlow()

    // Infinite-scroll episode (bukan tombol next/prev kayak web referensi):
    // fetch per halaman (page API mulai dari 0), nambah ke list yang udah
    // ada tiap kali loadMoreEpisodesIfNeeded() dipanggil dari scroll listener
    // di DetailScreen. Anime episode banyak (One Piece dkk) jadi langsung
    // nampilin halaman pertama, bukan nunggu SEMUA halaman kebaca dulu kayak
    // getAllEpisodes() (itu masih dipakai PlayerScreen buat next/prev nav).
    private var episodeNextPage = 0
    private var episodesHasMore = true
    private var isFetchingMoreEpisodes = false
    private val episodesAccum = mutableListOf<EpisodeItem>()

    private val _isLoadingMoreEpisodes = MutableStateFlow(false)
    val isLoadingMoreEpisodes: StateFlow<Boolean> = _isLoadingMoreEpisodes.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.isFavorite(animeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val watchHistory: StateFlow<WatchHistoryEntity?> = repository.getHistoryForAnime(animeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Semua episode anime ini yang lagi/udah didownload, dipakai
    // EpisodeHorizontalCard buat nampilin status per-episode (belum ada,
    // progress, selesai, gagal) tanpa perlu masuk ke PlayerScreen dulu.
    val downloads: StateFlow<List<DownloadedEpisodeEntity>> = repository.downloadsForAnime(animeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _downloadErrorMessage = MutableStateFlow<String?>(null)
    val downloadErrorMessage: StateFlow<String?> = _downloadErrorMessage.asStateFlow()

    // Dialog pilih kualitas download -- null berarti tertutup. Nyimpen
    // episode yang lagi diproses (karena list-nya banyak episode, beda
    // sama PlayerViewModel yang cuma satu episode aktif).
    private val _downloadQualityPicker = MutableStateFlow<DetailDownloadPickerState?>(null)
    val downloadQualityPicker: StateFlow<DetailDownloadPickerState?> = _downloadQualityPicker.asStateFlow()

    // ---- Tab Season / Cuplix / Cover / Poster ------------------------------
    // Dimuat malas: baru diminta saat tab-nya pertama kali dibuka.
    private val _seasons = MutableStateFlow(PagedTabState<AnimeItem>(hasMore = false))
    val seasons: StateFlow<PagedTabState<AnimeItem>> = _seasons.asStateFlow()

    private val _cuplix = MutableStateFlow(PagedTabState<CuplixItem>())
    val cuplix: StateFlow<PagedTabState<CuplixItem>> = _cuplix.asStateFlow()

    private val _covers = MutableStateFlow(PagedTabState<GalleryImage>())
    val covers: StateFlow<PagedTabState<GalleryImage>> = _covers.asStateFlow()

    private val _posters = MutableStateFlow(PagedTabState<GalleryImage>())
    val posters: StateFlow<PagedTabState<GalleryImage>> = _posters.asStateFlow()

    private val cuplixPager = TabPager(_cuplix, keyOf = { it.id }) { page ->
        when (val r = repository.getMovieCuplixPage(animeId, page)) {
            is Result.Success -> Result.Success(r.data.items)
            is Result.Error -> r
            is Result.Loading -> Result.Loading
        }
    }
    private val coverPager = TabPager(_covers, keyOf = { it.id }) { page ->
        repository.getMovieGallery(GalleryKind.COVER, animeId, page)
    }
    private val posterPager = TabPager(_posters, keyOf = { it.id }) { page ->
        repository.getMovieGallery(GalleryKind.POSTER, animeId, page)
    }

    /** Dipanggil saat tab dipilih -- memuat datanya kalau belum pernah. */
    fun ensureTabLoaded(tab: DetailTab) {
        when (tab) {
            DetailTab.SEASON -> loadSeasons(force = false)
            DetailTab.CUPLIX -> cuplixPager.ensureLoaded()
            DetailTab.COVER -> coverPager.ensureLoaded()
            DetailTab.POSTER -> posterPager.ensureLoaded()
            DetailTab.EPISODE -> Unit
        }
    }

    fun retryTab(tab: DetailTab) {
        when (tab) {
            DetailTab.SEASON -> loadSeasons(force = true)
            DetailTab.CUPLIX -> cuplixPager.retry()
            DetailTab.COVER -> coverPager.retry()
            DetailTab.POSTER -> posterPager.retry()
            DetailTab.EPISODE -> Unit
        }
    }

    fun loadMoreCuplix() = cuplixPager.loadMore()
    fun loadMoreCovers() = coverPager.loadMore()
    fun loadMorePosters() = posterPager.loadMore()

    private fun loadSeasons(force: Boolean) {
        val s = _seasons.value
        if (s.isLoading || (s.loaded && !force)) return
        _seasons.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getSeasons(animeId)) {
                is Result.Success -> _seasons.update {
                    it.copy(items = result.data, isLoading = false, loaded = true, hasMore = false)
                }
                is Result.Error -> _seasons.update { it.copy(isLoading = false, error = result.message) }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Paginasi generik satu tab. [page] mulai dari 0; kalau halaman 0 kosong
     * tapi halaman 1 ada isinya, API dianggap mulai dari 1 (belum terverifikasi
     * untuk endpoint ini). Item duplikat dibuang; halaman yang tidak menambah
     * item baru dianggap halaman terakhir.
     */
    private inner class TabPager<T>(
        private val state: MutableStateFlow<PagedTabState<T>>,
        private val keyOf: (T) -> String,
        private val fetch: suspend (page: Int) -> Result<List<T>>
    ) {
        private var nextPage = 0
        private val seen = HashSet<String>()
        private var job: Job? = null

        fun ensureLoaded() {
            val s = state.value
            if (!s.loaded && !s.isLoading) load(reset = true)
        }

        fun retry() {
            if (state.value.items.isEmpty()) load(reset = true) else loadMore()
        }

        fun loadMore() {
            val s = state.value
            if (s.loaded && s.hasMore && !s.isLoading && !s.isLoadingMore) load(reset = false)
        }

        private fun load(reset: Boolean) {
            job?.cancel()
            if (reset) {
                nextPage = 0
                seen.clear()
            }
            state.update {
                if (reset) {
                    it.copy(items = emptyList(), isLoading = true, isLoadingMore = false, error = null, hasMore = true, loaded = false)
                } else {
                    it.copy(isLoadingMore = true, error = null)
                }
            }
            job = viewModelScope.launch {
                var page = nextPage
                var result = fetch(page)
                if (page == 0 && result is Result.Success && result.data.isEmpty()) {
                    val alt = fetch(1)
                    if (alt is Result.Success && alt.data.isNotEmpty()) {
                        result = alt
                        page = 1
                    }
                }
                val finalResult = result
                val finalPage = page
                when (finalResult) {
                    is Result.Success -> {
                        val fresh = finalResult.data.filter { seen.add(keyOf(it)) }
                        nextPage = finalPage + 1
                        state.update {
                            it.copy(
                                items = it.items + fresh,
                                isLoading = false,
                                isLoadingMore = false,
                                hasMore = fresh.isNotEmpty(),
                                loaded = true
                            )
                        }
                    }
                    is Result.Error -> state.update {
                        it.copy(isLoading = false, isLoadingMore = false, error = finalResult.message)
                    }
                    is Result.Loading -> Unit
                }
            }
        }
    }

    init {
        loadDetail()
        loadEpisodes()
        loadPremiumStatus()
        loadPreview()
    }

    /** Buka dialog pilih kualitas buat satu episode, fetch server list FRESH. */
    fun openDownloadQualityPicker(episode: EpisodeItem) {
        _downloadQualityPicker.value = DetailDownloadPickerState(episode = episode)
        viewModelScope.launch {
            when (val result = repository.getDownloadQualityOptions(episode.id)) {
                is Result.Success -> _downloadQualityPicker.value =
                    DetailDownloadPickerState(episode = episode, options = result.data)
                is Result.Error -> _downloadQualityPicker.value =
                    DetailDownloadPickerState(episode = episode, errorMessage = result.message)
                else -> Unit
            }
        }
    }

    fun dismissDownloadQualityPicker() {
        _downloadQualityPicker.value = null
    }

    /** User udah milih kualitas -- mulai download episode yang lagi dipilih. */
    fun confirmDownloadQuality(server: StreamServer) {
        val episode = _downloadQualityPicker.value?.episode ?: return
        _downloadQualityPicker.value = null
        val animeTitle = (_detailState.value as? Result.Success)?.data?.title ?: "Anime"
        val posterUrl = (_detailState.value as? Result.Success)?.data?.image_poster
        viewModelScope.launch {
            val result = repository.enqueueEpisodeDownload(
                episodeId = episode.id,
                animeId = animeId,
                animeTitle = animeTitle,
                posterUrl = posterUrl,
                episodeTitle = episode.title,
                episodeIndex = episode.index,
                server = server,
                episodeThumbnailUrl = episode.resolvedImageUrl
            )
            if (result is Result.Error) {
                _downloadErrorMessage.value = result.message
            }
        }
    }

    fun deleteDownload(episodeId: String) {
        viewModelScope.launch {
            repository.deleteEpisodeDownload(episodeId)
        }
    }

    fun clearDownloadError() {
        _downloadErrorMessage.value = null
    }

    private fun loadPreview() {
        viewModelScope.launch {
            // Cukup ambil halaman pertama (episode 1 selalu ada di sana) --
            // gak perlu nunggu semua halaman episode kebaca dulu cuma buat
            // preview.
            val firstPageResult = repository.getEpisodes(animeId, page = 0).first { it !is Result.Loading }
            val episodes = (firstPageResult as? Result.Success)?.data ?: return@launch
            val firstEpisode = episodes.find { it.index?.trim() == "1" } ?: return@launch

            val streamResult = repository.getEpisodeStream(firstEpisode.id).first { it !is Result.Loading }
            val link = (streamResult as? Result.Success)?.data?.servers?.firstOrNull()?.link
            if (!link.isNullOrBlank()) {
                _previewUrl.value = link
            }
            // Gagal ambil stream / gak ada server -- biarin null, DetailScreen
            // otomatis fallback ke poster statis (lihat HeroPreviewPlayer).
        }
    }

    private fun loadPremiumStatus() {
        val uid = firebaseUid
        if (uid.isNullOrBlank()) return
        viewModelScope.launch {
            PremiumRepository().checkPremiumStatus(uid)
                .onSuccess { _isPremium.value = it.isPremium }
                // Gagal cek (misal offline) -- biarin default false (non-premium)
                // biar UI konservatif nge-lock, bukan malah nampilin semua kebuka.
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            repository.getDetail(animeId).collect { result ->
                _detailState.value = result
            }
        }
    }

    /** Reset & ambil halaman pertama (dipanggil pas layar dibuka / retry). */
    fun loadEpisodes() {
        episodeNextPage = 0
        episodesHasMore = true
        isFetchingMoreEpisodes = false
        episodesAccum.clear()
        viewModelScope.launch {
            _episodesState.value = Result.Loading
            when (val result = repository.getEpisodes(animeId, page = 0).first { it !is Result.Loading }) {
                is Result.Success -> {
                    episodesAccum.addAll(result.data)
                    episodesHasMore = result.data.isNotEmpty()
                    episodeNextPage = 1
                    _episodesState.value = Result.Success(episodesAccum.toList())
                }
                is Result.Error -> {
                    episodesHasMore = false
                    _episodesState.value = result
                }
                else -> {}
            }
        }
    }

    /**
     * Dipanggil dari scroll listener di DetailScreen (bukan tombol) tiap
     * kali user udah deket ujung bawah list episode -- auto nambah halaman
     * berikutnya kalau masih ada & lagi gak proses fetch lain.
     */
    fun loadMoreEpisodesIfNeeded() {
        if (isFetchingMoreEpisodes || !episodesHasMore) return
        if (_episodesState.value !is Result.Success) return
        isFetchingMoreEpisodes = true
        viewModelScope.launch {
            _isLoadingMoreEpisodes.value = true
            try {
                when (val result = repository.getEpisodes(animeId, page = episodeNextPage).first { it !is Result.Loading }) {
                    is Result.Success -> {
                        episodesHasMore = result.data.isNotEmpty()
                        if (result.data.isNotEmpty()) {
                            episodesAccum.addAll(result.data)
                            episodeNextPage++
                            _episodesState.value = Result.Success(episodesAccum.toList())
                        }
                    }
                    else -> episodesHasMore = false
                }
            } finally {
                _isLoadingMoreEpisodes.value = false
                isFetchingMoreEpisodes = false
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val currentDetail = (_detailState.value as? Result.Success)?.data ?: return@launch
            repository.toggleFavorite(currentDetail, isFavorite.value)
        }
    }
}

/**
 * State dialog pilih kualitas download di DetailScreen. [episode] nunjukin
 * lagi milihin kualitas buat episode yang mana (karena satu layar ini
 * nampilin banyak episode sekaligus). options == null && errorMessage ==
 * null berarti masih loading.
 */
data class DetailDownloadPickerState(
    val episode: EpisodeItem,
    val options: List<StreamServer>? = null,
    val errorMessage: String? = null
)
