package com.example.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.AnimeItem
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * All the anime airing on a single day, keyed by its position (0 = Monday ... 6 = Sunday).
 */
data class DaySection(
    val dayIndex: Int,
    val dayKey: String,
    val dayName: String,
    val anime: List<AnimeItem>
)

class ScheduleViewModel(private val repository: AnimeRepository) : ViewModel() {

    val daysApiKeys = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    val daysDisplayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    private val _scheduleState = MutableStateFlow<Result<List<DaySection>>>(Result.Loading)
    val scheduleState: StateFlow<Result<List<DaySection>>> = _scheduleState.asStateFlow()

    init {
        loadFullWeek()
    }

    fun loadFullWeek() {
        viewModelScope.launch {
            _scheduleState.value = Result.Loading
            try {
                val sections = daysApiKeys.indices
                    .map { index ->
                        async { index to fetchDay(daysApiKeys[index]) }
                    }
                    .awaitAll()
                    .sortedBy { it.first }
                    .map { (index, list) ->
                        DaySection(
                            dayIndex = index,
                            dayKey = daysApiKeys[index],
                            dayName = daysDisplayNames[index],
                            anime = list
                        )
                    }
                _scheduleState.value = Result.Success(sections)
            } catch (e: Exception) {
                _scheduleState.value = Result.Error(e, e.localizedMessage ?: "Gagal memuat jadwal tayang")
            }
        }
    }

    private suspend fun fetchDay(dayKey: String): List<AnimeItem> {
        return when (val result = repository.getSchedule(dayKey).first { it !is Result.Loading }) {
            is Result.Success -> result.data
            else -> emptyList()
        }
    }
}
