package com.example.ui.screens.donghua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnichinAnimeDetail
import com.example.data.repository.AnichinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [slug] bisa slug ANIME ("renegade-immortal", dari /anime, /search, /genre) atau
 * slug EPISODE ("renegade-immortal-episode-160-subtitle-indonesia", dari tab Terbaru
 * yang ngambil dari GET /). Endpoint GET /{slug} cuma paham slug anime, jadi slug
 * episode harus diubah dulu ke slug anime induknya.
 */
class DonghuaDetailViewModel(
    private val repository: AnichinRepository,
    private val slug: String
) : ViewModel() {

    private val _state = MutableStateFlow<Result<AnichinAnimeDetail>>(Result.Loading)
    val state: StateFlow<Result<AnichinAnimeDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = Result.Loading

            if (!EPISODE_SLUG.containsMatchIn(slug)) {
                var direct = fetchDetail(slug)

                // Slug film/episode tanpa "-episode-N" (mis. "xxx-movie-subtitle-indonesia")
                // dibalas API sebagai "Unknown Title" karena endpoint detail cuma paham slug anime.
                // Cari slug anime induknya lewat "root" episode, atau buang ekor "-subtitle-indonesia".
                if (direct.isUnknownTitle()) {
                    val ep = repository.getEpisode(slug).first { it !is Result.Loading }
                    val root = (ep as? Result.Success)?.data?.root
                    val candidates = listOfNotNull(root, slug.replace(SUBTITLE_TAIL, ""))
                        .filter { it.isNotBlank() && it != slug }
                        .distinct()
                    for (candidate in candidates) {
                        val r = fetchDetail(candidate)
                        if (!r.isUnknownTitle()) {
                            direct = r
                            break
                        }
                    }
                }
                _state.value = direct
                return@launch
            }

            // 1) Tebak slug anime dari slug episode (buang "-episode-160-subtitle-indonesia")
            val derived = slug.replace(EPISODE_TAIL, "")
            var result = fetchDetail(derived)

            // 2) Tebakan meleset (typo di slug, dll) -> tanya /episode/{slug} buat dapetin "root"
            if (result.isUnusable()) {
                val ep = repository.getEpisode(slug).first { it !is Result.Loading }
                val root = (ep as? Result.Success)?.data?.root
                if (!root.isNullOrBlank() && root != derived) {
                    result = fetchDetail(root)
                }
            }
            _state.value = result
        }
    }

    private suspend fun fetchDetail(target: String): Result<AnichinAnimeDetail> =
        repository.getDetail(target).first { it !is Result.Loading }

    private fun Result<AnichinAnimeDetail>.isUnknownTitle(): Boolean =
        this is Result.Success && data.name == "Unknown Title"

    private fun Result<AnichinAnimeDetail>.isUnusable(): Boolean = when (this) {
        is Result.Error -> true
        is Result.Success -> data.name == "Unknown Title" || data.episodes.isEmpty()
        is Result.Loading -> false
    }

    private companion object {
        // cocok "episode" maupun typo "epsiode" yang ada di beberapa slug situs sumber
        val EPISODE_SLUG = Regex("-ep[a-z]*sode-\\d+")
        val EPISODE_TAIL = Regex("-ep[a-z]*sode-\\d+.*$")
        val SUBTITLE_TAIL = Regex("-(subtitle|sub)-?(indonesia|indo|indonesa).*$", RegexOption.IGNORE_CASE)
    }
}
