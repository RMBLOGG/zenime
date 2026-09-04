package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListItem
import com.example.data.model.BacakomikListResponse
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State list komik yang bisa "Load More" -- dipakai buat keempat mode
 * (Terbaru, Populer, Search, Genre). "hasNextPage" ngikutin field yang
 * dikasih API di tiap response (lihat BacakomikListResponse).
 */
data class ComicListState(
    val items: List<BacakomikListItem> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isInitialLoading && errorMessage == null && items.isEmpty()
}

class ComicViewModel(private val repository: ComicRepository) : ViewModel() {

    private val _latestState = MutableStateFlow(ComicListState())
    val latestState: StateFlow<ComicListState> = _latestState.asStateFlow()

    private val _popularState = MutableStateFlow(ComicListState())
    val popularState: StateFlow<ComicListState> = _popularState.asStateFlow()

    private val _genres = MutableStateFlow<Result<List<BacakomikGenreItem>>>(Result.Loading)
    val genres: StateFlow<Result<List<BacakomikGenreItem>>> = _genres.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dipake bareng buat mode search MAUPUN filter genre -- cuma satu yang
    // aktif dalam satu waktu (lihat isFiltering di ComicScreen).
    private val _filterState = MutableStateFlow(ComicListState(isInitialLoading = false))
    val filterState: StateFlow<ComicListState> = _filterState.asStateFlow()

    private val _selectedGenre = MutableStateFlow<BacakomikGenreItem?>(null)
    val selectedGenre: StateFlow<BacakomikGenreItem?> = _selectedGenre.asStateFlow()

    private var searchJob: Job? = null
    private var filterLoadJob: Job? = null

    init {
        loadLatest()
        loadPopular()
        loadGenres()
    }

    fun loadLatest(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _latestState.value = ComicListState(isInitialLoading = true)
            repository.getLatest(page = 1, forceRefresh = forceRefresh).collect { res ->
                _latestState.value = mapFirstPage(res)
            }
        }
    }

    fun loadMoreLatest() {
        val current = _latestState.value
        if (current.isLoadingMore || !current.hasNextPage) return
        viewModelScope.launch {
            _latestState.value = current.copy(isLoadingMore = true)
            val nextPage = current.currentPage + 1
            repository.getLatest(page = nextPage).collect { res ->
                _latestState.value = mergeNextPage(current, res, nextPage)
            }
        }
    }

    fun loadPopular(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _popularState.value = ComicListState(isInitialLoading = true)
            repository.getPopular(page = 1, forceRefresh = forceRefresh).collect { res ->
                _popularState.value = mapFirstPage(res)
            }
        }
    }

    fun loadMorePopular() {
        val current = _popularState.value
        if (current.isLoadingMore || !current.hasNextPage) return
        viewModelScope.launch {
            _popularState.value = current.copy(isLoadingMore = true)
            val nextPage = current.currentPage + 1
            repository.getPopular(page = nextPage).collect { res ->
                _popularState.value = mergeNextPage(current, res, nextPage)
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            repository.getGenres().collect { _genres.value = it }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        filterLoadJob?.cancel()
        if (query.isBlank()) {
            _filterState.value = ComicListState(isInitialLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce - jangan nembak API tiap keystroke
            _filterState.value = ComicListState(isInitialLoading = true)
            repository.search(query, page = 1).collect { res ->
                _filterState.value = mapFirstPage(res)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        filterLoadJob?.cancel()
        _searchQuery.value = ""
        _filterState.value = ComicListState(isInitialLoading = false)
    }

    fun selectGenre(genre: BacakomikGenreItem?) {
        searchJob?.cancel()
        filterLoadJob?.cancel()
        _searchQuery.value = ""
        _selectedGenre.value = genre
        if (genre == null) {
            _filterState.value = ComicListState(isInitialLoading = false)
            return
        }
        filterLoadJob = viewModelScope.launch {
            _filterState.value = ComicListState(isInitialLoading = true)
            repository.getByGenre(genre.slug, page = 1).collect { res ->
                _filterState.value = mapFirstPage(res)
            }
        }
    }

    // Load more buat mode filter -- otomatis lanjut ke sumber yang lagi
    // aktif (search kalau query keisi, genre kalau lagi milih genre).
    fun loadMoreFilter() {
        val current = _filterState.value
        if (current.isLoadingMore || !current.hasNextPage) return
        val query = _searchQuery.value
        val genre = _selectedGenre.value
        if (query.isBlank() && genre == null) return

        viewModelScope.launch {
            _filterState.value = current.copy(isLoadingMore = true)
            val nextPage = current.currentPage + 1
            val flow = if (query.isNotBlank()) {
                repository.search(query, page = nextPage)
            } else {
                repository.getByGenre(genre!!.slug, page = nextPage)
            }
            flow.collect { res -> _filterState.value = mergeNextPage(current, res, nextPage) }
        }
    }

    private fun mapFirstPage(res: Result<BacakomikListResponse>): ComicListState = when (res) {
        is Result.Loading -> ComicListState(isInitialLoading = true)
        is Result.Error -> ComicListState(isInitialLoading = false, errorMessage = res.message)
        is Result.Success -> ComicListState(
            items = res.data.komikList ?: emptyList(),
            isInitialLoading = false,
            hasNextPage = res.data.hasNextPage ?: false,
            currentPage = res.data.currentPage ?: 1
        )
    }

    private fun mergeNextPage(current: ComicListState, res: Result<BacakomikListResponse>, requestedPage: Int): ComicListState =
        when (res) {
            is Result.Loading -> current
            is Result.Error -> current.copy(isLoadingMore = false) // gagal load more -> diem aja, biarin retry via tombol lagi
            is Result.Success -> {
                val newItems = res.data.komikList ?: emptyList()
                // Kalau halaman baru ternyata kosong / gak nambah apa-apa,
                // anggap udah abis -- matiin hasNextPage biar gak infinite loop.
                val stillHasNext = (res.data.hasNextPage ?: false) && newItems.isNotEmpty()
                current.copy(
                    items = current.items + newItems,
                    isLoadingMore = false,
                    hasNextPage = stillHasNext,
                    currentPage = res.data.currentPage ?: requestedPage
                )
            }
        }
}
