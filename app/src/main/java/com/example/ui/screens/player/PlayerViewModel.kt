package com.example.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.EpisodeItem
import com.example.data.model.StreamResponse
import com.example.data.model.StreamServer
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: AnimeRepository,
    val episodeId: String,
    val animeId: String
) : ViewModel() {

    private val _streamState = MutableStateFlow<Result<StreamResponse>>(Result.Loading)
    val streamState: StateFlow<Result<StreamResponse>> = _streamState.asStateFlow()

    private val _selectedServer = MutableStateFlow<StreamServer?>(null)
    val selectedServer: StateFlow<StreamServer?> = _selectedServer.asStateFlow()

    val defaultQuality: StateFlow<String> = repository.userPrefs.defaultQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "720p"
        )

    val autoSkipIntro: StateFlow<Boolean> = repository.userPrefs.autoSkipIntroFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val autoSkipOutro: StateFlow<Boolean> = repository.userPrefs.autoSkipOutroFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private var currentAnimeTitle: String = "Anime"
    private var currentPosterUrl: String? = null

    // Posisi terakhir nonton episode INI (bukan episode lain di anime yang
    // sama) -- 0 kalau belum pernah nonton, atau kalau progress lama udah
    // mepet abis (dianggap "udah kelar" jadi gak perlu resume).
    private val _resumePositionMs = MutableStateFlow(0L)
    val resumePositionMs: StateFlow<Long> = _resumePositionMs.asStateFlow()

    // Daftar semua episode anime ini, buat ditampilin di sidebar "Daftar
    // Episode" di PlayerScreen. Di-load sekali di init, sama kayak
    // loadAnimeInfo() -- repository udah nge-cache jadi murah dipanggil lagi
    // kalau user buka-tutup sidebar atau pindah episode dalam anime yang sama.
    private val _episodeListState = MutableStateFlow<Result<List<EpisodeItem>>>(Result.Loading)
    val episodeListState: StateFlow<Result<List<EpisodeItem>>> = _episodeListState.asStateFlow()

    init {
        loadStream()
        loadAnimeInfo()
        loadResumePosition()
        loadEpisodeList()
    }

    private fun loadEpisodeList() {
        viewModelScope.launch {
            repository.getAllEpisodes(animeId).collect { result ->
                _episodeListState.value = result
            }
        }
    }

    private fun loadResumePosition() {
        viewModelScope.launch {
            val history = repository.getHistoryForAnime(animeId).first()
            if (history != null && history.episodeId == episodeId) {
                val isNearlyFinished = history.durationMs > 0 &&
                    history.progressMs >= history.durationMs * 0.95
                if (!isNearlyFinished && history.progressMs > 5000) {
                    _resumePositionMs.value = history.progressMs
                }
            }
        }
    }

    private fun loadAnimeInfo() {
        viewModelScope.launch {
            repository.getDetail(animeId).collect { result ->
                if (result is Result.Success) {
                    currentAnimeTitle = result.data.title ?: "Anime"
                    currentPosterUrl = result.data.image_poster
                }
            }
        }
    }

    fun loadStream() {
        viewModelScope.launch {
            _streamState.value = Result.Loading
            repository.getEpisodeStream(episodeId).collect { result ->
                _streamState.value = result
                if (result is Result.Success) {
                    val servers = result.data.servers ?: emptyList()
                    // Baca langsung dari DataStore, jangan lewat StateFlow defaultQuality
                    // (yang WhileSubscribed & belum tentu ke-collect duluan sebelum ini jalan)
                    val prefQuality = repository.userPrefs.defaultQualityFlow.first()
                    // Pick server with matching preferred quality or first available
                    val matchedServer = servers.find { it.quality?.contains(prefQuality) == true }
                        ?: servers.firstOrNull()
                    _selectedServer.value = matchedServer
                }
            }
        }
    }

    fun selectServer(server: StreamServer) {
        _selectedServer.value = server
    }

    fun saveProgress(progressMs: Long, durationMs: Long, epTitle: String?, epIndex: String?) {
        if (progressMs > 0 && durationMs > 0) {
            viewModelScope.launch {
                repository.saveWatchProgress(
                    animeId = animeId,
                    animeTitle = currentAnimeTitle,
                    posterUrl = currentPosterUrl,
                    episodeId = episodeId,
                    episodeTitle = epTitle ?: "Episode $epIndex",
                    episodeIndex = epIndex,
                    progressMs = progressMs,
                    durationMs = durationMs
                )
            }
        }
    }
}
