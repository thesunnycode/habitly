package com.habitly.app.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.data.prefs.habitlyDataStore
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
class AiPrefsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // DataStore persists to app storage that Robolectric shares across tests in this class.
    @Before
    fun clearPrefs() { runBlocking { context.habitlyDataStore.edit { it.clear() } } }

    @Test fun defaults() = runBlocking {
        val p = AiPrefs(context)
        assertTrue(p.aiEnabled.first())
        assertFalse(p.aiConsent.first())
        assertFalse(p.demoAi.first())
        assertTrue(p.showForecast.first())
        assertNull(p.reviewBannerDismissedWeek.first())
        assertNull(p.forecastModelJson.first())
        assertNull(p.reviewCacheJson.first())
    }

    @Test fun settersRoundTrip() = runBlocking {
        val p = AiPrefs(context)
        p.setAiEnabled(false); p.setAiConsent(true); p.setDemoAi(true); p.setShowForecast(false)
        p.setReviewBannerDismissedWeek("2026-09-28"); p.setForecastModelJson("{\"a\":1}"); p.setReviewCacheJson("[]")
        assertFalse(p.aiEnabled.first())
        assertTrue(p.aiConsent.first())
        assertTrue(p.demoAi.first())
        assertFalse(p.showForecast.first())
        assertEquals("2026-09-28", p.reviewBannerDismissedWeek.first())
        assertEquals("{\"a\":1}", p.forecastModelJson.first())
        assertEquals("[]", p.reviewCacheJson.first())
    }

    @Test fun dailyAllowanceCountsDownAndResetsTheNextDay() = runBlocking {
        val p = AiPrefs(context)
        val day = java.time.LocalDate.of(2026, 10, 2)
        assertEquals(2, p.consumeDailyAi(limit = 3, today = day))
        assertEquals(1, p.consumeDailyAi(limit = 3, today = day))
        assertEquals(0, p.consumeDailyAi(limit = 3, today = day))
        assertNull(p.consumeDailyAi(limit = 3, today = day))
        assertEquals(2, p.consumeDailyAi(limit = 3, today = day.plusDays(1)))
    }
}
