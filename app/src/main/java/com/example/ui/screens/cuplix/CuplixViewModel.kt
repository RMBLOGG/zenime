package com.example.ui.screens.cuplix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.CuplixItem
import com.example.data.model.CuplixPage
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class CuplixUiState(
    val items: List<CuplixItem> = emptyList(),
    val sort: String = CuplixViewModel.SORT_POPULAR,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

/**
 * Feed Cuplix gaya scroll vertikal. Paginasi pakai id yang sudah tampil
 * (key_id_fyp) + kursor dari server; klip yang sudah pernah tampil dibuang
 * (dedupe), dan kalau satu batch isinya cuma duplikat feed dianggap habis
 * supaya tidak minta halaman berikutnya terus-menerus.
 */
class CuplixViewModel(
    private val repository: AnimeRepository,
    // Non-null = feed khusus klip milik satu anime (dibuka dari tab Cuplix di
    // halaman detail). Null = feed global seperti biasa.
    private val movieId: String? = null
) : ViewModel() {

    /** Feed per-anime tidak punya pilihan urutan (pakai type=NEW dari API). */
    val movieScoped: Boolean get() = movieId != null

    companion object {
        const val SORT_POPULAR = "scroll_likes"
        const val SORT_NEW = "scroll_new"
        const val SORT_OLD = "scroll_old"
    }

    private val _state = MutableStateFlow(CuplixUiState())
    val state: StateFlow<CuplixUiState> = _state.asStateFlow()

    private val seenIds = LinkedHashSet<String>()
    private var cursors: Map<String, String> = emptyMap()
    private var hasMore = true
    private var loadJob: Job? = null
    private var nextMoviePage = 0   // hanya dipakai di mode per-anime (page API mulai 0)

    // URL video per episode. Dipakai buat prefetch klip berikutnya; dibuang
    // kalau pemutaran gagal (link bisa kedaluwarsa).
    private val urlCache = ConcurrentHashMap<String, String>()

    init {
        load(reset = true)
    }

    fun setSort(sort: String) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        load(reset = true)
    }

    fun retry() {
        if (_state.value.items.isEmpty()) load(reset = true) else loadMore()
    }

    fun loadMore() {
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (!reset && (loadJob?.isActive == true || !hasMore)) return
        loadJob?.cancel()
        if (reset) {
            seenIds.clear()
            cursors = emptyMap()
            nextMoviePage = 0
            hasMore = true
        }
        val sort = _state.value.sort
        _state.update {
            if (reset) {
                it.copy(items = emptyList(), isLoading = true, isLoadingMore = false, error = null)
            } else {
                it.copy(isLoadingMore = true, error = null)
            }
        }
        loadJob = viewModelScope.launch {
            when (val result = fetchPage(sort)) {
                is Result.Success -> {
                    val fresh = result.data.items.filter { seenIds.add(it.id) }
                    cursors = result.data.cursors
                    hasMore = result.data.hasMore && fresh.isNotEmpty()
                    _state.update {
                        it.copy(
                            items = it.items + fresh,
                            isLoading = false,
                            isLoadingMore = false
                        )
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(isLoading = false, isLoadingMore = false, error = result.message)
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun fetchPage(sort: String): Result<CuplixPage> {
        val movie = movieId ?: return repository.getCuplixPage(sort, seenIds.toList(), cursors)

        var page = nextMoviePage
        var result = repository.getMovieCuplixPage(movie, page)
        // Kalau ternyata API mulai dari 1: halaman 0 kosong tapi halaman 1 ada isinya.
        if (page == 0 && result is Result.Success && result.data.items.isEmpty()) {
            val alt = repository.getMovieCuplixPage(movie, 1)
            if (alt is Result.Success && alt.data.items.isNotEmpty()) {
                result = alt
                page = 1
            }
        }
        if (result is Result.Success) nextMoviePage = page + 1
        return result
    }

    suspend fun videoUrl(episodeId: String): Result<String> {
        urlCache[episodeId]?.let { return Result.Success(it) }
        val result = repository.getCuplixVideoUrl(episodeId)
        if (result is Result.Success) urlCache[episodeId] = result.data
        return result
    }

    fun prefetch(episodeId: String) {
        if (urlCache.containsKey(episodeId)) return
        viewModelScope.launch { videoUrl(episodeId) }
    }

    fun forgetUrl(episodeId: String) {
        urlCache.remove(episodeId)
    }
}
