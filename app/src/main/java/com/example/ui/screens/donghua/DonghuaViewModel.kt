package com.example.ui.screens.donghua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnichinCard
import com.example.data.model.AnichinGenre
import com.example.data.model.AnichinHomeSection
import com.example.data.model.AnichinListResponse
import com.example.data.repository.AnichinRepository
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DonghuaTab(val label: String) {
    LATEST("Terbaru"),
    ONGOING("Ongoing"),
    ALL("Semua")
}

/** State grid donghua yang bisa "Muat Lebih Banyak". */
data class DonghuaGridState(
    val items: List<AnichinCard> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val page: Int = 1,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isInitialLoading && errorMessage == null && items.isEmpty()
}

data class DonghuaHomeState(
    val sections: List<AnichinHomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && sections.isEmpty()
}

class DonghuaViewModel(private val repository: AnichinRepository) : ViewModel() {

    private val _homeState = MutableStateFlow(DonghuaHomeState())
    val homeState: StateFlow<DonghuaHomeState> = _homeState.asStateFlow()

    private val _ongoingState = MutableStateFlow(DonghuaGridState())
    val ongoingState: StateFlow<DonghuaGridState> = _ongoingState.asStateFlow()

    private val _allState = MutableStateFlow(DonghuaGridState())
    val allState: StateFlow<DonghuaGridState> = _allState.asStateFlow()

    // Dipakai bareng buat mode search DAN filter genre (cuma satu aktif sekali waktu)
    private val _filterState = MutableStateFlow(DonghuaGridState(isInitialLoading = false))
    val filterState: StateFlow<DonghuaGridState> = _filterState.asStateFlow()

    private val _genres = MutableStateFlow<List<AnichinGenre>>(emptyList())
    val genres: StateFlow<List<AnichinGenre>> = _genres.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow<AnichinGenre?>(null)
    val selectedGenre: StateFlow<AnichinGenre?> = _selectedGenre.asStateFlow()

    private var searchJob: Job? = null
    private var ongoingStarted = false
    private var allStarted = false

    init {
        loadHome()
        loadGenres()
    }

    // ---------- Beranda (GET /) ----------

    fun loadHome() {
        viewModelScope.launch {
            repository.getHome().collect { res ->
                _homeState.value = when (res) {
                    is Result.Loading -> DonghuaHomeState(isLoading = true)
                    is Result.Success -> DonghuaHomeState(
                        sections = res.data.results.orEmpty()
                            .map { sec -> sec.copy(cards = sec.cards.orEmpty().filter { !it.slug.isNullOrBlank() }.distinctBy { it.slug }) }
                            .filter { !it.cards.isNullOrEmpty() },
                        isLoading = false
                    )
                    is Result.Error -> DonghuaHomeState(
                        isLoading = false,
                        errorMessage = friendlyErrorMessage(res.exception, "Gagal memuat donghua.")
                    )
                }
            }
        }
    }

    // ---------- Tab Ongoing / Semua (GET /anime) ----------

    fun onTabSelected(tab: DonghuaTab) {
        when (tab) {
            DonghuaTab.ONGOING -> if (!ongoingStarted) { ongoingStarted = true; loadOngoing(1) }
            DonghuaTab.ALL -> if (!allStarted) { allStarted = true; loadAll(1) }
            DonghuaTab.LATEST -> Unit
        }
    }

    fun loadOngoing(page: Int) = loadPaged(_ongoingState, page) { p ->
        repository.getAnimeList(
            status = "Ongoing",
            order = "update",
            extra = if (p > 1) mapOf("page" to p.toString()) else emptyMap()
        )
    }

    fun loadAll(page: Int) = loadPaged(_allState, page) { p ->
        repository.getAnimeList(
            extra = if (p > 1) mapOf("page" to p.toString()) else emptyMap()
        )
    }

    fun loadMoreOngoing() = loadMore(_ongoingState, ::loadOngoing)
    fun loadMoreAll() = loadMore(_allState, ::loadAll)

    // ---------- Genre (GET /genres, /genre/{slug}) ----------

    private fun loadGenres() {
        viewModelScope.launch {
            repository.getGenres().collect { res ->
                if (res is Result.Success) _genres.value = res.data
            }
        }
    }

    fun selectGenre(genre: AnichinGenre?) {
        _selectedGenre.value = genre
        searchJob?.cancel()
        if (genre == null) {
            if (_searchQuery.value.isBlank()) _filterState.value = DonghuaGridState(isInitialLoading = false)
            return
        }
        _searchQuery.value = ""
        loadGenrePage(1)
    }

    private fun loadGenrePage(page: Int) {
        val slug = _selectedGenre.value?.slug ?: return
        loadPaged(_filterState, page) { p -> repository.getByGenre(slug, p) }
    }

    fun loadMoreFilter() {
        if (_selectedGenre.value != null) loadMore(_filterState, ::loadGenrePage)
        // Hasil search (GET /search/{query}) gak dipaginasi, jadi gak ada "muat lebih banyak".
    }

    // ---------- Search (GET /search/{query}) ----------

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            if (_selectedGenre.value == null) _filterState.value = DonghuaGridState(isInitialLoading = false)
            return
        }
        _selectedGenre.value = null
        searchJob = viewModelScope.launch {
            delay(500) // debounce, endpoint scraping lumayan berat
            _filterState.value = DonghuaGridState(isInitialLoading = true)
            repository.search(query).collect { res ->
                _filterState.value = when (res) {
                    is Result.Loading -> DonghuaGridState(isInitialLoading = true)
                    is Result.Success -> DonghuaGridState(
                        items = res.data.results.orEmpty().validCards(),
                        isInitialLoading = false,
                        hasNextPage = false
                    )
                    is Result.Error -> DonghuaGridState(
                        isInitialLoading = false,
                        errorMessage = friendlyErrorMessage(res.exception, "Gagal mencari donghua.")
                    )
                }
            }
        }
    }

    fun clearSearch() = onSearchQueryChange("")

    fun retryFilter() {
        if (_searchQuery.value.isNotBlank()) onSearchQueryChange(_searchQuery.value)
        else selectGenre(_selectedGenre.value)
    }

    // ---------- helper paging ----------

    private fun List<AnichinCard>.validCards(): List<AnichinCard> =
        filter { !it.slug.isNullOrBlank() && it.slug != "unknown" }.distinctBy { it.slug }

    private fun loadMore(state: MutableStateFlow<DonghuaGridState>, load: (Int) -> Unit) {
        val current = state.value
        if (current.isLoadingMore || !current.hasNextPage) return
        load(current.page + 1)
    }

    private fun loadPaged(
        state: MutableStateFlow<DonghuaGridState>,
        page: Int,
        fetch: (Int) -> Flow<Result<AnichinListResponse>>
    ) {
        viewModelScope.launch {
            val before = state.value
            state.value = if (page == 1) DonghuaGridState(isInitialLoading = true)
            else before.copy(isLoadingMore = true)

            fetch(page).collect { res ->
                when (res) {
                    is Result.Loading -> Unit
                    is Result.Success -> {
                        val incoming = res.data.results.orEmpty().validCards()
                        val existing = if (page == 1) emptyList() else before.items
                        val known = existing.map { it.slug }.toSet()
                        val fresh = incoming.filter { it.slug !in known }
                        state.value = DonghuaGridState(
                            items = existing + fresh,
                            isInitialLoading = false,
                            isLoadingMore = false,
                            // Berhenti kalau halaman baru gak nambah item sama sekali
                            hasNextPage = fresh.isNotEmpty(),
                            page = page
                        )
                    }
                    is Result.Error -> {
                        state.value = if (page == 1) DonghuaGridState(
                            isInitialLoading = false,
                            errorMessage = friendlyErrorMessage(res.exception, "Gagal memuat donghua.")
                        ) else before.copy(isLoadingMore = false)
                    }
                }
            }
        }
    }
}
