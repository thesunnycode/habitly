package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

private val ONBOARDING_KEY = booleanPreferencesKey("has_completed_onboarding")
private val NOTIFICATION_PROMPT_KEY = booleanPreferencesKey("notification_prompt_shown")
private val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")

class OnboardingPrefs(private val context: Context) {
    val hasCompletedOnboarding = context.habitlyDataStore.data.map { it[ONBOARDING_KEY] ?: false }

    /**
     * Whether the reminders explainer sheet has been shown. It is deliberately held back until the
     * user has logged something: a permission prompt during onboarding is one of the biggest
     * drop-off points there is, and at that stage a reminder has no value to offer yet.
     */
    val hasShownNotificationPrompt =
        context.habitlyDataStore.data.map { it[NOTIFICATION_PROMPT_KEY] ?: false }

    /**
     * Whether the system permission dialog has been launched at least once. Android reports "don't
     * ask again" only as a missing rationale, which is also what it reports before the first ask, so
     * this is what tells a permanent denial apart from a fresh install.
     */
    val hasRequestedNotificationPermission =
        context.habitlyDataStore.data.map { it[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false }

    suspend fun setNotificationPermissionRequested() {
        context.habitlyDataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED_KEY] = true }
    }

    suspend fun setCompleted() {
        context.habitlyDataStore.edit { it[ONBOARDING_KEY] = true }
    }

    suspend fun setNotificationPromptShown() {
        context.habitlyDataStore.edit { it[NOTIFICATION_PROMPT_KEY] = true }
    }
}
