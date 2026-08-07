package com.example.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnimeItem
import com.example.data.model.GenreItem
import com.example.data.model.SearchResponse
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: AnimeRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedGenre = MutableStateFlow<String?>(null)
    val selectedStatus = MutableStateFlow<String?>(null)
    val selectedType = MutableStateFlow<String?>(null)
    val selectedSort = MutableStateFlow<String?>(null)

    private val _searchResults = MutableStateFlow<Result<List<AnimeItem>>>(Result.Success(emptyList()))
    val searchResults: StateFlow<Result<List<AnimeItem>>> = _searchResults.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreItem>>(emptyList())
    val genres: StateFlow<List<GenreItem>> = _genres.asStateFlow()

    private var currentPage = 1
    private var hasNextPage = false
    private val allLoadedItems = mutableListOf<AnimeItem>()

    init {
        loadGenres()
        observeQuery()
        performSearch()
    }

    private fun loadGenres() {
        viewModelScope.launch {
            repository.getGenres().collect { result ->
                if (result is Result.Success) {
                    _genres.value = result.data
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .collect {
                    performSearch()
                }
        }
    }

    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    fun applyFilter(genre: String?, status: String?, type: String?, sort: String?) {
        selectedGenre.value = genre
        selectedStatus.value = status
        selectedType.value = type
        selectedSort.value = sort
        performSearch()
    }

    fun resetFilter() {
        selectedGenre.value = null
        selectedStatus.value = null
        selectedType.value = null
        selectedSort.value = null
        performSearch()
    }

    fun performSearch() {
        currentPage = 1
        allLoadedItems.clear()
        fetchPage(1)
    }

    fun loadNextPage() {
        if (hasNextPage && _searchResults.value !is Result.Loading) {
            currentPage++
            fetchPage(currentPage)
        }
    }

    private fun fetchPage(page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _searchResults.value = Result.Loading
            }
            repository.search(
                query = searchQuery.value,
                page = page,
                sort = selectedSort.value,
                genreIn = selectedGenre.value,
                status = selectedStatus.value,
                type = selectedType.value
            ).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (page == 1) _searchResults.value = Result.Loading
                    }
                    is Result.Error -> {
                        if (page == 1) {
                            _searchResults.value = Result.Error(result.exception, result.message)
                        }
                    }
                    is Result.Success -> {
                        val response = result.data
                        val newItems = response.results ?: emptyList()
                        if (page == 1) {
                            allLoadedItems.clear()
                        }
                        allLoadedItems.addAll(newItems)
                        hasNextPage = response.next_page != null
                        _searchResults.value = Result.Success(allLoadedItems.toList())
                    }
                }
            }
        }
    }
}
