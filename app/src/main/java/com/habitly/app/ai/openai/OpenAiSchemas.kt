package com.habitly.app.ai.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Strict structured-output schemas for the three replies. They mirror the app's response DTOs
 * ([com.habitly.app.ai.SuggestResponse], [com.habitly.app.ai.ReviewResponse],
 * [com.habitly.app.ai.CoachResponse]) so a reply decodes straight into them. Strict mode requires every
 * property to be listed as required and no extra properties; optional values are typed as nullable.
 */
internal object OpenAiSchemas {
    private val WEEKDAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    private val ACTION_TYPES = listOf("set_reminder", "set_days", "set_rest_day", "set_goal", "log_done", "rest_today", "open_habit")

    private fun type(vararg names: String): JsonElement =
        if (names.size == 1) JsonPrimitive(names[0]) else JsonArray(names.map(::JsonPrimitive))

    private fun scalar(vararg names: String) = buildJsonObject { put("type", type(*names)) }

    private fun enumOf(values: List<String>) = buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(values.map(::JsonPrimitive)))
    }

    private fun array(items: JsonElement) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    /** Arrays and objects are made nullable with anyOf, the form strict mode documents for them. */
    private fun nullable(schema: JsonElement) = buildJsonObject {
        put("anyOf", buildJsonArray { add(schema); add(buildJsonObject { put("type", "null") }) })
    }

    private fun obj(vararg props: Pair<String, JsonElement>) = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props.toMap()))
        put("required", buildJsonArray { props.forEach { add(JsonPrimitive(it.first)) } })
        put("additionalProperties", false)
    }

    private val frequency = obj(
        "type" to enumOf(listOf("DAILY", "SPECIFIC_DAYS", "INTERVAL")),
        "days" to nullable(array(enumOf(WEEKDAYS))),
        "intervalDays" to scalar("integer", "null"),
    )

    private val action = obj(
        "type" to enumOf(ACTION_TYPES),
        "habitId" to scalar("integer"),
        "time" to scalar("string", "null"),
        "days" to nullable(array(enumOf(WEEKDAYS))),
        "date" to scalar("string", "null"),
        "count" to scalar("integer", "null"),
        "label" to scalar("string"),
    )

    val SUGGEST: JsonObject = obj(
        "intent" to enumOf(listOf("sleep", "fitness", "focus", "health", "mind", "other")),
        "habits" to array(
            obj(
                "name" to scalar("string"),
                "iconKey" to scalar("string"),
                "colorSeed" to scalar("integer"),
                "category" to scalar("string"),
                "frequency" to frequency,
                "targetCount" to scalar("integer", "null"),
                "unit" to scalar("string", "null"),
                "reminderTime" to scalar("string", "null"),
                "why" to scalar("string"),
            ),
        ),
    )

    val REVIEW: JsonObject = obj(
        "pages" to array(
            obj(
                "kind" to enumOf(listOf("summary", "win", "pattern", "tip")),
                "headline" to scalar("string"),
                "body" to scalar("string"),
                "habitId" to scalar("integer", "null"),
                "action" to nullable(action),
            ),
        ),
    )

    val COACH: JsonObject = obj(
        "reply" to scalar("string"),
        "actions" to array(action),
    )
}
