package com.habitly.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiGatewayTest {
    private class Settings(enabled: Boolean = true, consent: Boolean = true, demo: Boolean = false) : AiSettingsSource {
        override val aiEnabled = MutableStateFlow(enabled)
        override val aiConsent = MutableStateFlow(consent)
        override val demoAi = MutableStateFlow(demo)
    }
    private class Api(val tag: String) : AiApi {
        var calls = 0
        override suspend fun suggestHabits(request: SuggestRequest) = AiResult.Failed("unused")
        override suspend fun weeklyReview(request: ReviewRequest) = AiResult.Failed("unused")
        override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> { calls++; return AiResult.Success(CoachResponse(tag), 7) }
    }
    private val req = CoachRequest("2026-09-30", emptyList(), emptyList())

    @Test fun disabledWins() = runTest {
        val g = AiGateway(Settings(enabled = false, demo = true), Api("http"), Api("demo"))
        assertEquals(AiResult.Disabled, g.call { coach(req) })
    }
    @Test fun demoNeedsNoConsentOrConfig() = runTest {
        val g = AiGateway(Settings(consent = false, demo = true), null, Api("demo"))
        assertEquals("demo", (g.call { coach(req) } as AiResult.Success).value.reply)
    }
    @Test fun consentRequiredBeforeHttp() = runTest {
        val http = Api("http")
        val g = AiGateway(Settings(consent = false), http, Api("demo"))
        assertEquals(AiResult.ConsentRequired, g.call { coach(req) })
        assertEquals(0, http.calls)
    }
    @Test fun notConfiguredWithoutHttp() = runTest {
        assertEquals(AiResult.NotConfigured, AiGateway(Settings(), null, Api("demo")).call { coach(req) })
    }
    @Test fun successUpdatesRemaining() = runTest {
        val g = AiGateway(Settings(), Api("http"), Api("demo"))
        g.call { coach(req) }
        assertEquals(7, g.remaining.value)
    }
}
