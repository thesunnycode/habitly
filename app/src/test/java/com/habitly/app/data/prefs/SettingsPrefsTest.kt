package com.habitly.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPrefsTest {

    // DataStore persists to real app storage that Robolectric shares across test methods in
    // this class run — clear it before each test so method execution order can't affect results.
    @Before
    fun clearPrefs() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            context.habitlyDataStore.edit { it.clear() }
        }
    }

    @Test
    fun themeModeDefaultsToDark() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
    }

    @Test
    fun setThemeModePersistsTheChoice() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())
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
