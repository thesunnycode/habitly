package com.habitly.app.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.habitly.app.data.prefs.habitlyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

interface AiSettingsSource {
    val aiEnabled: Flow<Boolean>
    val aiConsent: Flow<Boolean>
    val demoAi: Flow<Boolean>
}

class AiPrefs(private val context: Context) : AiSettingsSource {
    private val store get() = context.habitlyDataStore

    override val aiEnabled = store.data.map { it[AI_ENABLED] ?: true }
    override val aiConsent = store.data.map { it[AI_CONSENT] ?: false }
    override val demoAi = store.data.map { it[DEMO_AI] ?: false }
    val showForecast: Flow<Boolean> = store.data.map { it[SHOW_FORECAST] ?: true }
    val reviewBannerDismissedWeek: Flow<String?> = store.data.map { it[REVIEW_DISMISSED] }
    val forecastModelJson: Flow<String?> = store.data.map { it[FORECAST_MODEL] }
    val reviewCacheJson: Flow<String?> = store.data.map { it[REVIEW_CACHE] }

    suspend fun setAiEnabled(v: Boolean) { store.edit { it[AI_ENABLED] = v } }
    suspend fun setAiConsent(v: Boolean) { store.edit { it[AI_CONSENT] = v } }
    suspend fun setDemoAi(v: Boolean) { store.edit { it[DEMO_AI] = v } }
    suspend fun setShowForecast(v: Boolean) { store.edit { it[SHOW_FORECAST] = v } }
    suspend fun setReviewBannerDismissedWeek(week: String) { store.edit { it[REVIEW_DISMISSED] = week } }
    suspend fun setForecastModelJson(json: String) { store.edit { it[FORECAST_MODEL] = json } }
    suspend fun setReviewCacheJson(json: String) { store.edit { it[REVIEW_CACHE] = json } }

    suspend fun installId(): String {
        store.data.first()[INSTALL_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        var result = fresh
        store.edit { prefs -> result = prefs[INSTALL_ID] ?: fresh.also { prefs[INSTALL_ID] = it } }
        return result
    }

    private companion object {
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_CONSENT = booleanPreferencesKey("ai_consent")
        val DEMO_AI = booleanPreferencesKey("demo_ai")
        val SHOW_FORECAST = booleanPreferencesKey("show_forecast")
        val REVIEW_DISMISSED = stringPreferencesKey("review_banner_dismissed_week")
        val FORECAST_MODEL = stringPreferencesKey("forecast_model_json")
        val REVIEW_CACHE = stringPreferencesKey("review_cache_json")
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
