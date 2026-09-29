package com.example.ui.screens.donghua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnichinEpisodeDetail
import com.example.data.model.AnichinVideoSource
import com.example.data.repository.AnichinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dua request jalan PARALEL (masing-masing scraping ke situs sumber, bisa 5-8 detik):
 *  - /video-source/{slug}  -> link video (wajib, buat mulai nonton)
 *  - /episode/{slug}       -> judul + daftar episode buat tombol sebelumnya/berikutnya (opsional)
 * Player gak nunggu yang kedua.
 */
class DonghuaPlayerViewModel(
    private val repository: AnichinRepository,
    private val episodeSlug: String
) : ViewModel() {

    val slug: String get() = episodeSlug

    private val _videoState = MutableStateFlow<Result<AnichinVideoSource>>(Result.Loading)
    val videoState: StateFlow<Result<AnichinVideoSource>> = _videoState.asStateFlow()

    private val _episodeState = MutableStateFlow<Result<AnichinEpisodeDetail>>(Result.Loading)
    val episodeState: StateFlow<Result<AnichinEpisodeDetail>> = _episodeState.asStateFlow()

    init {
        loadVideo()
        loadEpisodeInfo()
    }

    fun loadVideo() {
        viewModelScope.launch {
            repository.getVideoSource(episodeSlug).collect { _videoState.value = it }
        }
    }

    private fun loadEpisodeInfo() {
        viewModelScope.launch {
            repository.getEpisode(episodeSlug).collect { _episodeState.value = it }
        }
    }
}
