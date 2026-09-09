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

class SearchViewModel(
    private val repository: AnimeRepository,
    initialStatus: String? = null
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val selectedGenre = MutableStateFlow<String?>(null)
    val selectedStatus = MutableStateFlow(initialStatus)
    val selectedType = MutableStateFlow<String?>(null)
    val selectedSort = MutableStateFlow<String?>(null)

    private val _searchResults = MutableStateFlow<Result<List<AnimeItem>>>(Result.Success(emptyList()))
    val searchResults: StateFlow<Result<List<AnimeItem>>> = _searchResults.asStateFlow()

    private val _genres = MutableStateFlow<List<GenreItem>>(emptyList())
    val genres: StateFlow<List<GenreItem>> = _genres.asStateFlow()

    // animeinweb (Dayynime v5) itu bukan pagination halaman biasa -- server bisa
    // nyisir beberapa halaman upstream sekaligus dalam 1 response biar filter
    // status/type gak gampang mentok, jadi page yang dikirim ke request
    // BERIKUTNYA harus ngikutin cursor `next_page` dari response sebelumnya,
    // bukan dihitung +1 sendiri di app. Pola yang sama dipakai di Aniku buat
    // source Dayynime-v5.
    private var nextPageCursor = 0
    private var hasNextPage = true
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
        nextPageCursor = 0
        hasNextPage = true
        allLoadedItems.clear()
        fetchPage(isFirstPage = true)
    }

    fun loadNextPage() {
        if (hasNextPage && _searchResults.value !is Result.Loading) {
            fetchPage(isFirstPage = false)
        }
    }

    private fun fetchPage(isFirstPage: Boolean) {
        viewModelScope.launch {
            if (isFirstPage) {
                _searchResults.value = Result.Loading
            }
            val apiPage = nextPageCursor
            repository.search(
                query = searchQuery.value,
                page = apiPage,
                sort = selectedSort.value,
                genreIn = selectedGenre.value,
                status = selectedStatus.value,
                type = selectedType.value
            ).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (isFirstPage) _searchResults.value = Result.Loading
                    }
                    is Result.Error -> {
                        if (isFirstPage) {
                            _searchResults.value = Result.Error(result.exception, result.message)
                        }
                        hasNextPage = false
                    }
                    is Result.Success -> {
                        val response = result.data
                        val newItems = response.results ?: emptyList()
                        if (isFirstPage) {
                            allLoadedItems.clear()
                        }
                        allLoadedItems.addAll(newItems)
                        hasNextPage = response.next_page != null
                        nextPageCursor = response.next_page ?: (apiPage + 1)
                        _searchResults.value = Result.Success(allLoadedItems.toList())
                    }
                }
            }
        }
    }
}
