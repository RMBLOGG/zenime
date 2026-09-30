package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.comic.ComicSourceId
import com.example.data.comic.ComicTab
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
 * State list komik yang bisa "Load More" -- dipakai buat tab aktif maupun
 * mode Search/Genre. "hasNextPage" ngikutin field yang dikasih API di tiap
 * response (lihat BacakomikListResponse).
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

    // Sumber yang tersedia, urutannya ngikutin NetworkModule.comicSources.
    val sources: List<ComicSourceId> = repository.availableSources.map { it.id }

    private val _selectedSource = MutableStateFlow(sources.first())
    val selectedSource: StateFlow<ComicSourceId> = _selectedSource.asStateFlow()

    // Tab daftar berbeda tiap sumber (BacaKomik 2 tab, Westmanga 15 tab, dst).
    private val _tabs = MutableStateFlow(repository.tabsOf(sources.first()))
    val tabs: StateFlow<List<ComicTab>> = _tabs.asStateFlow()

    private val _selectedTab = MutableStateFlow(_tabs.value.first().id)
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // Daftar untuk (sumber, tab) yang lagi aktif. Pindah tab = muat ulang
    // halaman 1 (cepat, repository nyimpen cache 5 menit per sumber+tab).
    private val _listState = MutableStateFlow(ComicListState())
    val listState: StateFlow<ComicListState> = _listState.asStateFlow()

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

    private var tabJob: Job? = null
    private var genresJob: Job? = null
    private var searchJob: Job? = null
    private var filterLoadJob: Job? = null

    init {
        loadTab()
        loadGenres()
    }

    fun selectSource(source: ComicSourceId) {
        if (source == _selectedSource.value) return
        searchJob?.cancel()
        filterLoadJob?.cancel()
        _searchQuery.value = ""
        _selectedGenre.value = null
        _filterState.value = ComicListState(isInitialLoading = false)

        _selectedSource.value = source
        val newTabs = repository.tabsOf(source)
        _tabs.value = newTabs
        _selectedTab.value = newTabs.first().id
        loadTab()
        loadGenres()
    }

    fun selectTab(tabId: String) {
        if (tabId == _selectedTab.value) return
        _selectedTab.value = tabId
        loadTab()
    }

    fun loadTab(forceRefresh: Boolean = false) {
        tabJob?.cancel()
        val source = _selectedSource.value
        val tab = _selectedTab.value
        tabJob = viewModelScope.launch {
            _listState.value = ComicListState(isInitialLoading = true)
            repository.browse(source, tab, page = 1, forceRefresh = forceRefresh).collect { res ->
                _listState.value = mapFirstPage(res)
            }
        }
    }

    fun loadMoreTab() {
        val current = _listState.value
        if (current.isLoadingMore || !current.hasNextPage) return
        val source = _selectedSource.value
        val tab = _selectedTab.value
        tabJob = viewModelScope.launch {
            _listState.value = current.copy(isLoadingMore = true)
            val nextPage = current.currentPage + 1
            repository.browse(source, tab, page = nextPage).collect { res ->
                _listState.value = mergeNextPage(current, res, nextPage)
            }
        }
    }

    private fun loadGenres() {
        genresJob?.cancel()
        val source = _selectedSource.value
        genresJob = viewModelScope.launch {
            repository.getGenres(source).collect { _genres.value = it }
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
        val source = _selectedSource.value
        searchJob = viewModelScope.launch {
            delay(400) // debounce - jangan nembak API tiap keystroke
            _filterState.value = ComicListState(isInitialLoading = true)
            repository.search(source, query, page = 1).collect { res ->
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
        val source = _selectedSource.value
        filterLoadJob = viewModelScope.launch {
            _filterState.value = ComicListState(isInitialLoading = true)
            repository.getByGenre(source, genre.slug, page = 1).collect { res ->
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
        val source = _selectedSource.value

        filterLoadJob = viewModelScope.launch {
            _filterState.value = current.copy(isLoadingMore = true)
            val nextPage = current.currentPage + 1
            val flow = if (query.isNotBlank()) {
                repository.search(source, query, page = nextPage)
            } else {
                repository.getByGenre(source, genre!!.slug, page = nextPage)
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
