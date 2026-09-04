package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListItem
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComicHomeData(
    val latest: List<BacakomikListItem>,
    val popular: List<BacakomikListItem>
)

class ComicViewModel(private val repository: ComicRepository) : ViewModel() {

    private val _homeState = MutableStateFlow<Result<ComicHomeData>>(Result.Loading)
    val homeState: StateFlow<Result<ComicHomeData>> = _homeState.asStateFlow()

    private val _genres = MutableStateFlow<Result<List<BacakomikGenreItem>>>(Result.Loading)
    val genres: StateFlow<Result<List<BacakomikGenreItem>>> = _genres.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // null = lagi gak nyari/filter -> HomeScreen komik nampilin tab Latest/Populer.
    private val _searchState = MutableStateFlow<Result<List<BacakomikListItem>>?>(null)
    val searchState: StateFlow<Result<List<BacakomikListItem>>?> = _searchState.asStateFlow()

    private val _selectedGenre = MutableStateFlow<BacakomikGenreItem?>(null)
    val selectedGenre: StateFlow<BacakomikGenreItem?> = _selectedGenre.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadHome()
        loadGenres()
    }

    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            var latest: List<BacakomikListItem>? = null
            var popular: List<BacakomikListItem>? = null

            launch {
                repository.getLatest(forceRefresh).collect { res ->
                    if (res is Result.Success) {
                        latest = res.data
                        popular?.let { _homeState.value = Result.Success(ComicHomeData(latest!!, it)) }
                    } else if (res is Result.Loading && _homeState.value !is Result.Success) {
                        _homeState.value = Result.Loading
                    } else if (res is Result.Error && _homeState.value !is Result.Success) {
                        _homeState.value = res
                    }
                }
            }
            launch {
                repository.getPopular(forceRefresh).collect { res ->
                    if (res is Result.Success) {
                        popular = res.data
                        latest?.let { _homeState.value = Result.Success(ComicHomeData(it, popular!!)) }
                    } else if (res is Result.Error && _homeState.value !is Result.Success) {
                        _homeState.value = res
                    }
                }
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
        if (query.isBlank()) {
            _searchState.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce - jangan nembak API tiap keystroke
            repository.search(query).collect { _searchState.value = it }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchState.value = null
    }

    fun selectGenre(genre: BacakomikGenreItem?) {
        _selectedGenre.value = genre
        if (genre == null) {
            _searchState.value = null
            return
        }
        clearSearchQueryOnly()
        viewModelScope.launch {
            repository.getByGenre(genre.slug).collect { _searchState.value = it }
        }
    }

    private fun clearSearchQueryOnly() {
        searchJob?.cancel()
        _searchQuery.value = ""
    }
}
