package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.extractChapterLabel
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComicReaderViewModel(
    private val repository: ComicRepository,
    initialChapterSlug: String,
    private val comicSlug: String,
    private val comicTitle: String?,
    private val comicCover: String?
) : ViewModel() {

    private val _chapterState = MutableStateFlow<Result<BacakomikChapterResponse>>(Result.Loading)
    val chapterState: StateFlow<Result<BacakomikChapterResponse>> = _chapterState.asStateFlow()

    // Slug chapter yang lagi kebaca sekarang -- dipakai layar buat nampilin
    // judul & mutusin tombol "Chapter Berikutnya/Sebelumnya" masih aktif atau enggak.
    private val _currentSlug = MutableStateFlow(initialChapterSlug)
    val currentSlug: StateFlow<String> = _currentSlug.asStateFlow()

    // Posisi scroll awal buat chapter yang lagi dibuka SEKARANG. Null =
    // belum diketahui (masih nunggu query DB), dipulihkan dari progress
    // tersimpan HANYA kalau user masuk persis di chapter yang sama kayak
    // progress terakhir (mis. lewat tombol "Lanjutkan Baca") -- kalau buka
    // chapter lain manual, selalu mulai dari atas (0, 0).
    private val _initialScrollPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    val initialScrollPosition: StateFlow<Pair<Int, Int>?> = _initialScrollPosition.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.getComicProgressOnce(comicSlug)
            _initialScrollPosition.value = if (saved != null && saved.chapterSlug == initialChapterSlug) {
                saved.scrollItemIndex to saved.scrollItemOffset
            } else {
                0 to 0
            }
        }
        loadChapter(initialChapterSlug, resetScroll = false)
    }

    fun loadChapter(chapterSlug: String, resetScroll: Boolean = true) {
        _currentSlug.value = chapterSlug
        if (resetScroll) _initialScrollPosition.value = 0 to 0
        viewModelScope.launch {
            repository.getChapter(chapterSlug).collect { result ->
                _chapterState.value = result
                if (result is Result.Success) {
                    val label = result.data.title?.takeIf { it.isNotBlank() } ?: extractChapterLabel(chapterSlug)
                    val pos = _initialScrollPosition.value ?: (0 to 0)
                    persistProgress(chapterSlug, label, pos.first, pos.second)
                }
            }
        }
    }

    // Dipanggil dari layar tiap posisi scroll berubah (sudah di-debounce di
    // sisi UI), biar "Lanjutkan Baca" balik ke posisi yang persis sama,
    // bukan cuma ke chapter-nya doang.
    fun updateScrollPosition(index: Int, offset: Int) {
        val label = (chapterState.value as? Result.Success)?.data?.title?.takeIf { it.isNotBlank() }
            ?: extractChapterLabel(currentSlug.value)
        persistProgress(currentSlug.value, label, index, offset)
    }

    private fun persistProgress(chapterSlug: String, chapterLabel: String, index: Int, offset: Int) {
        viewModelScope.launch {
            repository.saveComicProgress(
                comicSlug = comicSlug,
                comicTitle = comicTitle ?: comicSlug,
                comicCover = comicCover,
                chapterSlug = chapterSlug,
                chapterLabel = chapterLabel,
                scrollItemIndex = index,
                scrollItemOffset = offset
            )
        }
    }
}
