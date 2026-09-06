package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.local.ComicReadingProgressEntity
import com.example.data.model.BacakomikDetail
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ComicDetailViewModel(
    private val repository: ComicRepository,
    private val slug: String
) : ViewModel() {

    private val _detailState = MutableStateFlow<Result<BacakomikDetail>>(Result.Loading)
    val detailState: StateFlow<Result<BacakomikDetail>> = _detailState.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.isComicFavorite(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Progress baca terakhir buat komik ini -- null kalau belum pernah dibaca
    // sama sekali. Dipakai buat nampilin tombol "Lanjutkan Baca" di detail.
    val readingProgress: StateFlow<ComicReadingProgressEntity?> = repository.getComicProgress(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadDetail()
    }

    fun loadDetail(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getDetail(slug, forceRefresh).collect { _detailState.value = it }
        }
    }

    fun toggleFavorite() {
        val detail = (_detailState.value as? Result.Success)?.data ?: return
        val currentlyFavorite = isFavorite.value
        viewModelScope.launch {
            repository.toggleComicFavorite(
                slug = slug,
                title = detail.title ?: "Tanpa Judul",
                cover = detail.cover,
                status = detail.status,
                isCurrentlyFavorite = currentlyFavorite
            )
        }
    }
}
