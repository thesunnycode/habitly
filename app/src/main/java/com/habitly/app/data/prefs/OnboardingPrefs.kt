package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

private val ONBOARDING_KEY = booleanPreferencesKey("has_completed_onboarding")
private val NOTIFICATION_PROMPT_KEY = booleanPreferencesKey("notification_prompt_shown")

class OnboardingPrefs(private val context: Context) {
    val hasCompletedOnboarding = context.habitlyDataStore.data.map { it[ONBOARDING_KEY] ?: false }

    /**
     * Whether the notification permission has already been asked for. The ask is deliberately held
     * back until the user has logged something: a permission prompt during onboarding is one of the
     * biggest drop-off points there is, and at that stage a reminder has no value to offer yet.
     */
    val hasShownNotificationPrompt =
        context.habitlyDataStore.data.map { it[NOTIFICATION_PROMPT_KEY] ?: false }

    suspend fun setCompleted() {
        context.habitlyDataStore.edit { it[ONBOARDING_KEY] = true }
    }

    suspend fun setNotificationPromptShown() {
        context.habitlyDataStore.edit { it[NOTIFICATION_PROMPT_KEY] = true }
    }
}
