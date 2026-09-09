package com.example.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ComicFavoriteEntity
import com.example.data.local.ComicReadingProgressEntity
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesHistoryViewModel(
    private val repository: AnimeRepository,
    private val comicRepository: ComicRepository
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Semua episode yang lagi/udah didownload, LINTAS anime -- ditampilin di
    // tab "Download" (gabungan, beda sama per-anime yang ada di DetailScreen).
    val downloads: StateFlow<List<DownloadedEpisodeEntity>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val comicFavorites: StateFlow<List<ComicFavoriteEntity>> = comicRepository.comicFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // "Lanjutkan Baca" lintas komik -- diurutkan dari yang paling baru dibaca.
    val comicReadingProgress: StateFlow<List<ComicReadingProgressEntity>> = comicRepository.comicReadingProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteHistoryItem(animeId: String) {
        viewModelScope.launch {
            repository.deleteHistory(animeId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun removeFavorite(animeId: String) {
        viewModelScope.launch {
            repository.removeFavorite(animeId)
        }
    }

    fun deleteDownload(episodeId: String) {
        viewModelScope.launch {
            repository.deleteEpisodeDownload(episodeId)
        }
    }

    fun removeComicFavorite(comicSlug: String) {
        viewModelScope.launch {
            comicRepository.removeComicFavorite(comicSlug)
        }
    }

    fun deleteComicProgress(comicSlug: String) {
        viewModelScope.launch {
            comicRepository.deleteComicProgress(comicSlug)
        }
    }
}
