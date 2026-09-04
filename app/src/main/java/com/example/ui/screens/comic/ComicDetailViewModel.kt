package com.example.ui.screens.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.BacakomikDetail
import com.example.data.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComicDetailViewModel(
    private val repository: ComicRepository,
    private val slug: String
) : ViewModel() {

    private val _detailState = MutableStateFlow<Result<BacakomikDetail>>(Result.Loading)
    val detailState: StateFlow<Result<BacakomikDetail>> = _detailState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getDetail(slug, forceRefresh).collect { _detailState.value = it }
        }
    }
}
