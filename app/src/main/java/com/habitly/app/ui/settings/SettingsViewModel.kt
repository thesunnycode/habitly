package com.habitly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(
    private val prefs: SettingsPrefs,
    private val repository: HabitDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.themeMode, prefs.notificationsEnabled) { theme, notifications ->
                _uiState.value.copy(themeMode = theme, notificationsEnabled = notifications)
            }.collect { _uiState.value = it }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun onNotificationsToggle(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }

    fun onResetAllData() {
        viewModelScope.launch {
            val current = repository.observeHabitsWithStats(LocalDate.now()).first()
            current.forEach { repository.deleteHabit(it.habit) }
            _uiState.value = _uiState.value.copy(resetCompleted = true)
        }
    }
}
