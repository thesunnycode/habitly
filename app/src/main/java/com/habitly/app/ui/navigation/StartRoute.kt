package com.habitly.app.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Picks the first screen from the onboarding flag. The splash stays up until this returns, so a
 * DataStore read failure must not escape: it falls back to Onboarding, which is safe to repeat.
 */
suspend fun resolveStartRoute(hasCompletedOnboarding: Flow<Boolean>): String {
    val done = hasCompletedOnboarding.catch { if (it is IOException) emit(false) else throw it }.first()
    return if (done) Destination.Today.route else Destination.Onboarding.route
}
