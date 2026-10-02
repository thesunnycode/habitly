package com.habitly.app.ai.openai

import com.habitly.app.ai.AiApi
import com.habitly.app.ai.AiJson
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** A daily allowance kept on the phone; [consume] returns what is left, or null once it is used up. */
fun interface AiQuota {
    suspend fun consume(): Int?
}

/**
 * Talks to OpenAI directly with the key built into this APK. This build is meant for one personal
 * phone: anyone holding the APK can extract the key, so the APK must not be shared and the OpenAI
 * account should have a monthly spend limit. The daily [quota] caps accidental runaway use.
 */
class OpenAiApi(
    private val apiKey: String,
    private val model: String,
    private val quota: AiQuota,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.openai.com",
    private val json: Json = AiJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AiApi {

    override suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse> = call {
        val out = structured(SuggestResponse.serializer(), "habit_suggestions", OpenAiPrompts.SUGGEST, OpenAiSchemas.SUGGEST,
            json.encodeToString(SuggestRequest.serializer(), request), maxTokens = 2500)
        OpenAiCleaner.suggest(out, request)
    }

    override suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse> = call {
        if (request.habits.isEmpty()) throw BadRequest()
        val out = structured(ReviewResponse.serializer(), "week_review", OpenAiPrompts.REVIEW, OpenAiSchemas.REVIEW,
            json.encodeToString(ReviewRequest.serializer(), request), maxTokens = 4000)
        OpenAiCleaner.review(out, request)
    }

    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> = call {
        val last = request.messages.lastOrNull()
        if (last?.role == "user" && flagged(last.text)) {
            CoachResponse(OpenAiCleaner.SAFE_REPLY, emptyList())
        } else {
            val out = structured(CoachResponse.serializer(), "coach_reply", OpenAiPrompts.COACH, OpenAiSchemas.COACH,
                json.encodeToString(CoachRequest.serializer(), request), maxTokens = 2000)
            OpenAiCleaner.coach(out, request)
        }
    }

    private class BadRequest : Exception()
    private class HttpFailure(val code: Int) : Exception()

    /** Counts the call against today's allowance, then maps every failure to an [AiResult]. */
    private suspend fun <T> call(block: suspend () -> T): AiResult<T> {
        val remaining = quota.consume() ?: return AiResult.RateLimited(null)
        return try {
            AiResult.Success(withRetry(block), remaining)
        } catch (e: HttpFailure) {
            when (e.code) {
                401, 403 -> AiResult.Failed("unauthorized")
                429 -> AiResult.RateLimited(null)
                else -> AiResult.Failed("server")
            }
        } catch (e: BadRequest) {
            AiResult.Failed("bad_request")
        } catch (e: UpstreamException) {
            AiResult.Failed("upstream")
        } catch (e: InterruptedIOException) {
            AiResult.Failed("timeout")
        } catch (e: IOException) {
            AiResult.Offline
        }
    }

    /** Like the proxy: an unusable answer or a 5xx gets one more try; everything else fails at once. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: UpstreamException) {
            // fall through to the second attempt
        } catch (e: HttpFailure) {
            if (e.code < 500) throw e
        }
        return block()
    }

    private suspend fun <R> structured(
        out: KSerializer<R>,
        name: String,
        system: String,
        schema: JsonObject,
        user: String,
        maxTokens: Int,
    ): R {
        val body = buildJsonObject {
            put("model", model)
            put("input", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
            put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("name", name)
                    put("schema", schema)
                    put("strict", true)
                })
            })
            put("max_output_tokens", maxTokens)
            // Reasoning models spend output tokens thinking; these are short, structured answers.
            if (model.startsWith("gpt-5") || model.startsWith("o")) put("reasoning", buildJsonObject { put("effort", "low") })
        }
        val response = post("/v1/responses", body)
        if (response["status"]?.jsonPrimitive?.contentOrNull == "incomplete") throw UpstreamException("incomplete")
        val content = response["output"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
            .flatMap { it["content"]?.jsonArray.orEmpty() }
            .map { it.jsonObject }
        if (content.any { it["type"]?.jsonPrimitive?.contentOrNull == "refusal" }) throw UpstreamException("refused")
        val text = content.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw UpstreamException("empty output")
        return try {
            json.decodeFromString(out, text)
        } catch (e: SerializationException) {
            throw UpstreamException("unparseable output")
        } catch (e: IllegalArgumentException) {
            throw UpstreamException("unparseable output")
        }
    }

    /** OpenAI's moderation check on what the user typed to the coach. */
    private suspend fun flagged(text: String): Boolean {
        val body = buildJsonObject {
            put("model", "omni-moderation-latest")
            put("input", text)
        }
        val results = post("/v1/moderations", body)["results"]?.jsonArray.orEmpty()
        return results.any { it.jsonObject["flagged"]?.jsonPrimitive?.booleanOrNull == true }
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject = withContext(io) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpFailure(response.code)
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: SerializationException) {
                throw UpstreamException("bad json")
            } catch (e: IllegalArgumentException) {
                throw UpstreamException("bad json")
            }
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
