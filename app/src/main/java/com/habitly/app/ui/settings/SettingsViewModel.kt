package com.habitly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.BuildConfig
import com.habitly.app.data.export.CsvExporter
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

class SettingsViewModel(
    private val prefs: SettingsPrefs,
    private val repository: HabitDataSource,
    /** Seeds the demo habits (debug builds only); injected because seeding needs the concrete repository and DAO. */
    private val loadDemoData: suspend () -> Unit = {},
    versionName: String = BuildConfig.VERSION_NAME,
    isDebug: Boolean = BuildConfig.DEBUG,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(versionName = versionName, isDebug = isDebug))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.themeMode,
                prefs.notificationsEnabled,
                prefs.weekStart,
                prefs.streakNudgeEnabled,
            ) { theme, notifications, weekStart, nudge ->
                _uiState.value.copy(
                    themeMode = theme,
                    notificationsEnabled = notifications,
                    weekStart = weekStart,
                    streakNudgeEnabled = nudge,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun onNotificationsToggle(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }

    fun onWeekStartChange(day: DayOfWeek) = viewModelScope.launch { prefs.setWeekStart(day) }

    fun onStreakNudgeToggle(enabled: Boolean) = viewModelScope.launch { prefs.setStreakNudgeEnabled(enabled) }

    /** Every habit and log as CSV text; the caller writes and shares the file. */
    suspend fun onExport(): String {
        val habits = repository.observeAllHabits().first()
        val logs = repository.observeAllLogs().first()
        return withContext(Dispatchers.Default) { CsvExporter.export(habits, logs) }
    }

    fun onLoadDemoData() {
        viewModelScope.launch { loadDemoData() }
    }

    fun onResetAllData() {
        viewModelScope.launch {
            // Archived habits too: "everything" includes what is out of sight.
            repository.observeAllHabits().first().forEach { repository.deleteHabit(it) }
            repository.clearAchievements()
            // A true fresh start: badges and the all-done moment celebrate again.
            prefs.clearSeenBadges()
            prefs.clearLastCelebratedDate()
            _uiState.value = _uiState.value.copy(resetCompleted = true)
        }
    }

    /** The screen has announced the finished reset; don't announce it again. */
    fun onResetMessageShown() {
        _uiState.value = _uiState.value.copy(resetCompleted = false)
    }
}
