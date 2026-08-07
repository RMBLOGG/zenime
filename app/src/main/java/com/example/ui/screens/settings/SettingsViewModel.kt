package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: AnimeRepository) : ViewModel() {

    val themeMode: StateFlow<String> = repository.userPrefs.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "DARK"
        )

    val dynamicColor: StateFlow<Boolean> = repository.userPrefs.dynamicColorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val defaultQuality: StateFlow<String> = repository.userPrefs.defaultQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "720p"
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.userPrefs.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.userPrefs.setDynamicColor(enabled)
        }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            repository.userPrefs.setDefaultQuality(quality)
        }
    }
}
