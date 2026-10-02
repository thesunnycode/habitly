package com.habitly.app.ui.review

import com.habitly.app.ai.AiJson
import com.habitly.app.ai.ReviewResponse
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** The last few generated reviews, keyed by the ISO date of the week's first day, so reopening costs no request. */
object ReviewCache {
    private val serializer = MapSerializer(String.serializer(), ReviewResponse.serializer())

    fun decode(json: String?): Map<String, ReviewResponse> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            AiJson.decodeFromString(serializer, json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun encode(map: Map<String, ReviewResponse>): String = AiJson.encodeToString(serializer, map)

    /** Adds or replaces [week], keeping only the [keep] most recent weeks (ISO dates sort chronologically). */
    fun put(map: Map<String, ReviewResponse>, week: String, review: ReviewResponse, keep: Int = 4): Map<String, ReviewResponse> =
        (map + (week to review)).toSortedMap().entries.toList().takeLast(keep).associate { it.key to it.value }
}
