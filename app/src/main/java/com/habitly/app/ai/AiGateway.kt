package com.habitly.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class AiGateway(
    private val settings: AiSettingsSource,
    private val http: AiApi?,
    private val demo: AiApi,
) {
    private val _remaining = MutableStateFlow<Int?>(null)
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()

    suspend fun <T> call(block: suspend AiApi.() -> AiResult<T>): AiResult<T> {
        if (!settings.aiEnabled.first()) return AiResult.Disabled
        if (settings.demoAi.first()) return demo.block()
        if (!settings.aiConsent.first()) return AiResult.ConsentRequired
        val api = http ?: return AiResult.NotConfigured
        return api.block().also { if (it is AiResult.Success) _remaining.value = it.remaining }
    }
}
