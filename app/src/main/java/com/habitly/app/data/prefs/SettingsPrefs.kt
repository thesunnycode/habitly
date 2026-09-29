package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val THEME_KEY = stringPreferencesKey("theme_mode")
private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
private val USER_NAME_KEY = stringPreferencesKey("user_name")
private val WEEK_START_KEY = stringPreferencesKey("week_start")
private val STREAK_NUDGE_KEY = booleanPreferencesKey("streak_nudge_enabled")
private val LAST_CELEBRATED_KEY = stringPreferencesKey("last_celebrated_date")
private val SEEN_BADGES_KEY = stringSetPreferencesKey("seen_badge_keys")

class SettingsPrefs(private val context: Context) : TodayPrefs, YouPrefs {

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

    override val userName = context.habitlyDataStore.data.map { it[USER_NAME_KEY] }

    override val weekStart = context.habitlyDataStore.data.map { prefs ->
        prefs[WEEK_START_KEY]?.let { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }
            ?: java.time.DayOfWeek.MONDAY
    }

    val streakNudgeEnabled = context.habitlyDataStore.data.map { it[STREAK_NUDGE_KEY] ?: true }

    override val lastCelebratedDate = context.habitlyDataStore.data.map { it[LAST_CELEBRATED_KEY] }

    val seenBadgeKeys = context.habitlyDataStore.data.map { it[SEEN_BADGES_KEY] ?: emptySet() }

    override suspend fun setUserName(name: String?) {
        context.habitlyDataStore.edit { prefs ->
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) prefs.remove(USER_NAME_KEY) else prefs[USER_NAME_KEY] = trimmed
        }
    }

    suspend fun setWeekStart(day: java.time.DayOfWeek) {
        context.habitlyDataStore.edit { it[WEEK_START_KEY] = day.name }
    }

    suspend fun setStreakNudgeEnabled(enabled: Boolean) {
        context.habitlyDataStore.edit { it[STREAK_NUDGE_KEY] = enabled }
    }

    override suspend fun setLastCelebratedDate(date: String) {
        context.habitlyDataStore.edit { it[LAST_CELEBRATED_KEY] = date }
    }

    suspend fun markBadgesSeen(keys: Set<String>) {
        context.habitlyDataStore.edit { it[SEEN_BADGES_KEY] = (it[SEEN_BADGES_KEY] ?: emptySet()) + keys }
    }
}
