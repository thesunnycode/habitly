package com.habitly.app.ui.settings

import com.habitly.app.data.prefs.ThemeMode
import java.time.DayOfWeek

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val notificationsEnabled: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val streakNudgeEnabled: Boolean = true,
    val versionName: String = "",
    val isDebug: Boolean = false,
    val resetCompleted: Boolean = false,
    val aiEnabled: Boolean = true,
    val showForecast: Boolean = true,
    val demoAi: Boolean = false,
    /** The Demo AI switch shows after 5 taps on the "AI features" heading, or whenever Demo AI is already on. */
    val demoAiRevealed: Boolean = false,
)
