package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.AnimeItem
import com.example.data.model.EpisodeItem
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: AnimeRepository,
    val animeId: String
) : ViewModel() {

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

    init {
        loadDetail()
        loadEpisodes()
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
