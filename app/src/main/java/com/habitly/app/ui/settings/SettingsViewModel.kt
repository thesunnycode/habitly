package com.habitly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.BuildConfig
import com.habitly.app.ai.AiPrefs
import com.habitly.app.data.export.CsvExporter
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.reminder.ReminderNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val aiPrefs: AiPrefs,
    /** Seeds the demo habits (debug builds only) and returns how many were added; injected because seeding needs the concrete repository and DAOs. */
    private val loadDemoData: suspend () -> Int = { 0 },
    versionName: String = BuildConfig.VERSION_NAME,
    isDebug: Boolean = BuildConfig.DEBUG,
    private val notifications: ReminderNotifications = ReminderNotifications.None,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(versionName = versionName, isDebug = isDebug))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Taps on the "AI features" heading; the fifth reveals the Demo AI switch. */
    private var aiHeaderTaps = 0

    init {
        viewModelScope.launch {
            val appPrefs = combine(
                prefs.themeMode,
                prefs.notificationsEnabled,
                prefs.weekStart,
                prefs.streakNudgeEnabled,
            ) { theme, notifications, weekStart, nudge ->
                { state: SettingsUiState ->
                    state.copy(
                        themeMode = theme,
                        notificationsEnabled = notifications,
                        weekStart = weekStart,
                        streakNudgeEnabled = nudge,
                    )
                }
            }
            val ai = combine(aiPrefs.aiEnabled, aiPrefs.showForecast, aiPrefs.demoAi) { enabled, forecast, demo ->
                { state: SettingsUiState ->
                    state.copy(
                        aiEnabled = enabled,
                        showForecast = forecast,
                        demoAi = demo,
                        // Once shown, the switch stays for this visit even after Demo AI is turned off.
                        demoAiRevealed = state.demoAiRevealed || demo,
                    )
                }
            }
            combine(appPrefs, ai) { applyPrefs, applyAi -> applyAi(applyPrefs(_uiState.value)) }
                .collect { _uiState.value = it }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun onNotificationsToggle(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }

    fun onWeekStartChange(day: DayOfWeek) = viewModelScope.launch { prefs.setWeekStart(day) }

    fun onStreakNudgeToggle(enabled: Boolean) = viewModelScope.launch { prefs.setStreakNudgeEnabled(enabled) }

    fun onAiEnabledToggle(enabled: Boolean) = viewModelScope.launch { aiPrefs.setAiEnabled(enabled) }

    fun onShowForecastToggle(enabled: Boolean) = viewModelScope.launch { aiPrefs.setShowForecast(enabled) }

    fun onDemoAiToggle(enabled: Boolean) = viewModelScope.launch { aiPrefs.setDemoAi(enabled) }

    /** Counts taps on the "AI features" heading; the fifth reveals the Demo AI switch. */
    fun onAiHeaderTapped() {
        if (_uiState.value.demoAiRevealed) return
        aiHeaderTaps++
        if (aiHeaderTaps >= 5) {
            _uiState.value = _uiState.value.copy(demoAiRevealed = true)
        }
    }

    /** Every habit and log as CSV text; the caller writes and shares the file. */
    suspend fun onExport(): String {
        val habits = repository.observeAllHabits().first()
        val logs = repository.observeAllLogs().first()
        return withContext(Dispatchers.Default) { CsvExporter.export(habits, logs) }
    }

    fun onLoadDemoData() {
        viewModelScope.launch {
            val added = loadDemoData()
            val message = if (added == 0) "Demo habits are already loaded" else "Added $added demo habits with 8 weeks of history"
            _uiState.value = _uiState.value.copy(demoMessage = message)
        }
    }

    fun onResetAllData() =
        viewModelScope.launch {
            // Leaving Settings mid-reset must not leave half the data behind, so it always finishes.
            withContext(NonCancellable) {
                // Archived habits too: "everything" includes what is out of sight.
                val ids = repository.observeAllHabits().first().map { it.id }
                repository.resetAll()
                // A reminder for a habit that no longer exists would open to nothing.
                ids.forEach(notifications::cancelFor)
                // A true fresh start: badges and the all-done moment celebrate again.
                prefs.clearSeenBadges()
                prefs.clearLastCelebratedDate()
            }
            _uiState.value = _uiState.value.copy(resetCompleted = true)
        }

    /** The screen has announced the finished reset; don't announce it again. */
    fun onResetMessageShown() {
        _uiState.value = _uiState.value.copy(resetCompleted = false)
    }

    /** The screen has shown the demo-data result; don't show it again. */
    fun onDemoMessageShown() {
        _uiState.value = _uiState.value.copy(demoMessage = null)
    }
}
