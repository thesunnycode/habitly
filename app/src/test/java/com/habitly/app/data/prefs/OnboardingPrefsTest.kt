package com.habitly.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingPrefsTest {

    @Test
    fun notificationPermissionRequestIsRememberedOnceMade() = runBlocking {
        val prefs = OnboardingPrefs(ApplicationProvider.getApplicationContext())
        prefs.setNotificationPermissionRequested()
        assertTrue(prefs.hasRequestedNotificationPermission.first())
    }
}
