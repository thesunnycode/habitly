package com.habitly.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPrefsTest {

    @Test
    fun themeModeDefaultsToSystem() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.first())
    }

    @Test
    fun setThemeModePersistsTheChoice() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
    }

    @Test
    fun notificationsEnabledDefaultsToTrue() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        assertEquals(true, prefs.notificationsEnabled.first())
    }

    @Test
    fun setNotificationsEnabledPersistsFalse() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.setNotificationsEnabled(false)
        assertEquals(false, prefs.notificationsEnabled.first())
    }
}
