package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

private val ONBOARDING_KEY = booleanPreferencesKey("has_completed_onboarding")

class OnboardingPrefs(private val context: Context) {
    val hasCompletedOnboarding = context.habitlyDataStore.data.map { it[ONBOARDING_KEY] ?: false }

    suspend fun setCompleted() {
        context.habitlyDataStore.edit { it[ONBOARDING_KEY] = true }
    }
}
