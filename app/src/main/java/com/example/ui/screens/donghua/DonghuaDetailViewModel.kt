package com.example.ui.screens.donghua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnichinAnimeDetail
import com.example.data.repository.AnichinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            repository.getDetail(slug).collect { _state.value = it }
        }
    }
}
