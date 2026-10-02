package com.habitly.app.ai.openai

import com.habitly.app.ai.AiResult
import com.habitly.app.ai.ChatMessageDto
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.SuggestRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class OpenAiApiTest {
    private val server = MockWebServer()
    private var left: Int? = 7
    private var consumed = 0
    private lateinit var api: OpenAiApi

    private val suggestReq = SuggestRequest("sleep earlier", listOf("Read"), listOf("sleep", "star"), listOf("Health", "General"))
    private val coachReq = CoachRequest("2026-10-02", emptyList(), listOf(ChatMessageDto("user", "hello")))

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        api = OpenAiApi(
            apiKey = "sk-test", model = "gpt-5-mini",
            quota = { consumed++; left },
            client = client, baseUrl = server.url("/").toString(),
        )
    }

    @After fun tearDown() = server.shutdown()

    /** A Responses API body whose message carries [text] as its output_text. */
    private fun responses(text: String, status: String = "completed") = MockResponse().setBody(
        buildString {
            append("""{"status":"$status","output":[{"type":"reasoning","summary":[]},""")
            append("""{"type":"message","content":[{"type":"output_text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}]}""")
        },
    )

    private fun moderation(flagged: Boolean) = MockResponse().setBody("""{"results":[{"flagged":$flagged}]}""")

    private val suggestion = """{"intent":"sleep","habits":[{"name":"Lights out 23:00","iconKey":"moon","colorSeed":42,
        "category":"Rest","frequency":{"type":"INTERVAL","days":null,"intervalDays":45},"targetCount":1,"unit":"x",
        "reminderTime":"9pm","why":"A fixed bedtime is easy."}]}"""

    @Test fun suggestSendsAStrictSchemaRequestAndCleansTheAnswer() = runBlocking {
        server.enqueue(responses(suggestion))
        val result = api.suggestHabits(suggestReq)

        val recorded = server.takeRequest()
        assertEquals("/v1/responses", recorded.path)
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("gpt-5-mini", body["model"]!!.jsonPrimitive.content)
        val format = body["text"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertTrue(format["strict"]!!.jsonPrimitive.boolean)
        assertEquals(OpenAiSchemas.SUGGEST, format["schema"] as JsonObject)
        val input = body["input"]!!.jsonArray
        assertEquals("system", input[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(input[1].jsonObject["content"]!!.jsonPrimitive.content.contains("sleep earlier"))

        val habit = (result as AiResult.Success).value.habits.single()
        assertEquals(7, result.remaining)
        assertEquals(1, consumed)
        assertEquals("star", habit.iconKey) // unknown icon → star
        assertEquals("General", habit.category) // unknown category → General
        assertEquals(11, habit.colorSeed)
        assertEquals("DAILY", habit.frequency.type) // interval 45 is out of range
        assertEquals(null, habit.targetCount)
        assertEquals(null, habit.unit)
        assertEquals(null, habit.reminderTime)
    }

    @Test fun anUnusableAnswerIsRetriedOnce() = runBlocking {
        server.enqueue(responses("{not json"))
        server.enqueue(responses(suggestion))
        assertTrue(api.suggestHabits(suggestReq) is AiResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test fun twoBadAnswersFail() = runBlocking {
        server.enqueue(responses(suggestion, status = "incomplete"))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(AiResult.Failed("server"), api.suggestHabits(suggestReq))
        assertEquals(2, server.requestCount)
    }

    @Test fun refusalIsAFailure() = runBlocking {
        val refusal = """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}"""
        server.enqueue(MockResponse().setBody(refusal))
        server.enqueue(MockResponse().setBody(refusal))
        assertEquals(AiResult.Failed("upstream"), api.suggestHabits(suggestReq))
    }

    @Test fun httpErrorsMapToResults() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(AiResult.Failed("unauthorized"), api.suggestHabits(suggestReq))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(AiResult.RateLimited(null), api.suggestHabits(suggestReq))
        // 4xx are not retried
        assertEquals(2, server.requestCount)
    }

    @Test fun noNetworkIsOffline() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AiResult.Offline, api.suggestHabits(suggestReq))
    }

    @Test fun usedUpAllowanceNeverCallsOpenAi() = runBlocking {
        left = null
        assertEquals(AiResult.RateLimited(null), api.suggestHabits(suggestReq))
        assertEquals(0, server.requestCount)
    }

    @Test fun flaggedCoachInputGetsTheSafeReplyWithoutAModelCall() = runBlocking {
        server.enqueue(moderation(flagged = true))
        val result = api.coach(coachReq) as AiResult.Success
        assertEquals(OpenAiCleaner.SAFE_REPLY, result.value.reply)
        assertTrue(result.value.actions.isEmpty())
        assertEquals("/v1/moderations", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun coachChecksModerationThenAsksTheModel() = runBlocking {
        server.enqueue(moderation(flagged = false))
        server.enqueue(responses("""{"reply":"${"word ".repeat(100).trim()}","actions":[]}"""))
        val result = api.coach(coachReq) as AiResult.Success
        assertEquals("/v1/moderations", server.takeRequest().path)
        assertEquals("/v1/responses", server.takeRequest().path)
        assertEquals(80, result.value.reply.split(" ").size) // 80 words, the last one carrying "…"
        assertTrue(result.value.reply.endsWith("…"))
    }

    @Test fun openingMessageSkipsModeration() = runBlocking {
        server.enqueue(responses("""{"reply":"Morning!","actions":[]}"""))
        api.coach(coachReq.copy(messages = emptyList()))
        assertEquals("/v1/responses", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun reviewWithoutHabitsIsNotSent() = runBlocking {
        assertEquals(AiResult.Failed("bad_request"), api.weeklyReview(ReviewRequest("2026-09-28", emptyList())))
        assertEquals(0, server.requestCount)
    }
}
