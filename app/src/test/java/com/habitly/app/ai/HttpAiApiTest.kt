package com.habitly.app.ai

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpAiApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpAiApi
    private val req = CoachRequest("2026-09-30", emptyList(), emptyList())

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        api = HttpAiApi(server.url("/").toString(), "tok", { "0b6f3f8e-2a4c-4d7a-9a3e-6c1f2b9d8e11" }, client)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun successParsesBodyAndRemaining() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply":"Hi","actions":[],"extra":1}""").addHeader("X-AI-Remaining", "12"))
        val r = api.coach(req)
        assertEquals(AiResult.Success(CoachResponse("Hi", emptyList()), 12), r)
        val recorded = server.takeRequest()
        assertEquals("/v1/coach", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
        assertEquals("0b6f3f8e-2a4c-4d7a-9a3e-6c1f2b9d8e11", recorded.getHeader("X-Device-Id"))
        assertTrue(recorded.body.readUtf8().contains("\"messages\":[]"))
    }

    @Test fun rateLimitedCarriesResetAt() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","resetAt":"2026-10-01T00:00:00.000Z"}"""))
        assertEquals(AiResult.RateLimited("2026-10-01T00:00:00.000Z"), api.coach(req))
    }

    @Test fun unauthorizedAndServerErrorsFail() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(AiResult.Failed("unauthorized"), api.coach(req))
        assertEquals(AiResult.Failed("server"), api.coach(req))
    }

    @Test fun malformedJsonFails() = runTest {
        server.enqueue(MockResponse().setBody("""{"nope":true}"""))
        assertEquals(AiResult.Failed("bad_response"), api.coach(req))
    }

    @Test fun connectionFailureIsOffline() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AiResult.Offline, api.coach(req))
    }

    @Test fun timeoutFails() = runTest {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        assertEquals(AiResult.Failed("timeout"), api.coach(req))
    }

    @Test fun pathsPerEndpoint() = runTest {
        server.enqueue(MockResponse().setBody("""{"intent":"sleep","habits":[]}"""))
        server.enqueue(MockResponse().setBody("""{"pages":[]}"""))
        api.suggestHabits(SuggestRequest("sleep", emptyList(), emptyList(), emptyList()))
        api.weeklyReview(ReviewRequest("2026-09-21", emptyList()))
        assertEquals("/v1/habits/suggest", server.takeRequest().path)
        assertEquals("/v1/review/weekly", server.takeRequest().path)
    }
}
