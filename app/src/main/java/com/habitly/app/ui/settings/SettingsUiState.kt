package com.habitly.app.ui.settings

import com.habitly.app.data.prefs.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val resetCompleted: Boolean = false,
)
