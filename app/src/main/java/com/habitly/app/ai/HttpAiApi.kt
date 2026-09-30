package com.habitly.app.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

class HttpAiApi(
    baseUrl: String,
    private val appToken: String,
    private val deviceId: suspend () -> String,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = AiJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AiApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun suggestHabits(request: SuggestRequest) =
        post("/v1/habits/suggest", request, SuggestRequest.serializer(), SuggestResponse.serializer())
    override suspend fun weeklyReview(request: ReviewRequest) =
        post("/v1/review/weekly", request, ReviewRequest.serializer(), ReviewResponse.serializer())
    override suspend fun coach(request: CoachRequest) =
        post("/v1/coach", request, CoachRequest.serializer(), CoachResponse.serializer())

    private suspend fun <Q, R> post(path: String, body: Q, qs: KSerializer<Q>, rs: KSerializer<R>): AiResult<R> {
        val id = deviceId()
        return withContext(io) {
            val request = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer $appToken")
                .header("X-Device-Id", id)
                .post(json.encodeToString(qs, body).toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> try {
                            AiResult.Success(json.decodeFromString(rs, text), response.header("X-AI-Remaining")?.toIntOrNull())
                        } catch (e: SerializationException) {
                            AiResult.Failed("bad_response")
                        } catch (e: IllegalArgumentException) {
                            AiResult.Failed("bad_response")
                        }
                        response.code == 429 -> AiResult.RateLimited(resetAt(text))
                        response.code == 401 -> AiResult.Failed("unauthorized")
                        else -> AiResult.Failed("server")
                    }
                }
            } catch (e: InterruptedIOException) {
                AiResult.Failed("timeout")
            } catch (e: IOException) {
                AiResult.Offline
            }
        }
    }

    private fun resetAt(text: String): String? = runCatching {
        json.parseToJsonElement(text).jsonObject["resetAt"]?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        private val JSON = "application/json".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
