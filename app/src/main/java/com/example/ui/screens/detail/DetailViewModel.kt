package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.AnimeItem
import com.example.data.model.EpisodeItem
import com.example.data.model.StreamServer
import com.example.data.repository.AnimeRepository
import com.example.data.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            val episodesResult = repository.getAllEpisodes(animeId).first { it !is Result.Loading }
            val episodes = (episodesResult as? Result.Success)?.data ?: return@launch
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

    fun loadEpisodes() {
        viewModelScope.launch {
            repository.getAllEpisodes(animeId).collect { result ->
                _episodesState.value = result
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
