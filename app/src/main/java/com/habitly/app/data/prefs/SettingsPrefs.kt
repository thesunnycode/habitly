package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val THEME_KEY = stringPreferencesKey("theme_mode")
private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

class SettingsPrefs(private val context: Context) {

    val themeMode = context.habitlyDataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    val notificationsEnabled = context.habitlyDataStore.data.map { it[NOTIFICATIONS_KEY] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.habitlyDataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.habitlyDataStore.edit { it[NOTIFICATIONS_KEY] = enabled }
    }
}
