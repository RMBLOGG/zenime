package com.example.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnimeItem
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: AnimeRepository) : ViewModel() {

    val selectedDayIndex = MutableStateFlow(0) // 0: monday, 1: tuesday, ... 6: sunday
    val daysApiKeys = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    val daysDisplayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    private val _scheduleState = MutableStateFlow<Result<List<AnimeItem>>>(Result.Loading)
    val scheduleState: StateFlow<Result<List<AnimeItem>>> = _scheduleState.asStateFlow()

    init {
        loadSchedule(daysApiKeys[0])
    }

    fun selectDay(index: Int) {
        selectedDayIndex.value = index
        loadSchedule(daysApiKeys[index])
    }

    fun loadSchedule(day: String) {
        viewModelScope.launch {
            repository.getSchedule(day).collect { result ->
                _scheduleState.value = result
            }
        }
    }
}
