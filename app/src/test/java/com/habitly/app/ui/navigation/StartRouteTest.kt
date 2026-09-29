package com.habitly.app.ui.navigation

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class StartRouteTest {
    @Test fun `a finished onboarding starts on Today`() = runTest {
        assertEquals(Destination.Today.route, resolveStartRoute(flowOf(true)))
    }

    @Test fun `a fresh install starts on Onboarding`() = runTest {
        assertEquals(Destination.Onboarding.route, resolveStartRoute(flowOf(false)))
    }

    @Test fun `an unreadable prefs file falls back to Onboarding instead of hanging the splash`() = runTest {
        val broken = flow<Boolean> { throw IOException("datastore corrupt") }
        assertEquals(Destination.Onboarding.route, resolveStartRoute(broken))
    }
}
