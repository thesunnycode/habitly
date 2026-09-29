package com.habitly.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun newPreferencesHaveSensibleDefaults() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        assertNull(prefs.userName.first())
        assertEquals(java.time.DayOfWeek.MONDAY, prefs.weekStart.first())
        assertTrue(prefs.streakNudgeEnabled.first())
        assertNull(prefs.lastCelebratedDate.first())
        assertTrue(prefs.seenBadgeKeys.first().isEmpty())
    }

    @Test
    fun newPreferencesRoundTrip() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.setUserName("Sunny")
        prefs.setWeekStart(java.time.DayOfWeek.SUNDAY)
        prefs.setStreakNudgeEnabled(false)
        prefs.setLastCelebratedDate("2026-09-29")
        prefs.markBadgesSeen(setOf("STREAK_7"))
        prefs.markBadgesSeen(setOf("FIRST_HABIT"))
        assertEquals("Sunny", prefs.userName.first())
        assertEquals(java.time.DayOfWeek.SUNDAY, prefs.weekStart.first())
        assertFalse(prefs.streakNudgeEnabled.first())
        assertEquals("2026-09-29", prefs.lastCelebratedDate.first())
        assertEquals(setOf("STREAK_7", "FIRST_HABIT"), prefs.seenBadgeKeys.first())
    }

    @Test
    fun blankNameIsStoredAsNoName() = runBlocking {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.setUserName("   ")
        assertNull(prefs.userName.first())
    }
}
