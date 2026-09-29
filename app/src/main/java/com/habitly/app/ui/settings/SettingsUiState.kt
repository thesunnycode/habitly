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
)
