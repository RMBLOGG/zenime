package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.BacakomikChapterResponse
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComicReaderViewModel(
    private val repository: ComicRepository,
    initialChapterSlug: String
) : ViewModel() {

    private val _chapterState = MutableStateFlow<Result<BacakomikChapterResponse>>(Result.Loading)
    val chapterState: StateFlow<Result<BacakomikChapterResponse>> = _chapterState.asStateFlow()

    // Slug chapter yang lagi kebaca sekarang -- dipakai layar buat nampilin
    // judul & mutusin tombol "Chapter Berikutnya/Sebelumnya" masih aktif atau enggak.
    private val _currentSlug = MutableStateFlow(initialChapterSlug)
    val currentSlug: StateFlow<String> = _currentSlug.asStateFlow()

    init {
        loadChapter(initialChapterSlug)
    }

    fun loadChapter(chapterSlug: String) {
        _currentSlug.value = chapterSlug
        viewModelScope.launch {
            repository.getChapter(chapterSlug).collect { _chapterState.value = it }
        }
    }
}
