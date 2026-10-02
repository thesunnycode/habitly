package com.habitly.app.ui.create

import com.habitly.app.ai.FrequencyDto
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.SuggestedHabitDto
import com.habitly.app.domain.DEFAULT_CATEGORIES
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.FrequencyValidator
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.ui.theme.HabitPalette
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** One AI idea, already cleaned up, with the model's one-line reason for it. */
data class Suggestion(val template: HabitTemplate, val why: String)

/**
 * The model's output is untrusted text. This turns each suggestion into a [HabitTemplate] the rest of
 * the app can use blindly: known icon, palette colour, listed category, a schedule and goal that pass
 * the same rules as the Add/Edit form, and no duplicate of something the user already tracks.
 */
object SuggestionSanitizer {
    private const val MAX_SUGGESTIONS = 4
    private const val MAX_NAME = 40
    private const val MAX_UNIT = 20
    private const val MAX_WHY = 90
    private val GOAL_RANGE = 2..999
    private val INTERVAL_RANGE = 2..30
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun sanitize(
        response: SuggestResponse,
        existingNames: Collection<String>,
        iconKeys: Collection<String>,
        palette: List<String> = HabitPalette.seeds,
    ): List<Suggestion> {
        val taken = existingNames.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val out = mutableListOf<Suggestion>()
        for (raw in response.habits) {
            if (out.size == MAX_SUGGESTIONS) break
            val name = raw.name.trim().take(MAX_NAME).trim()
            if (name.isEmpty() || !taken.add(name.lowercase())) continue
            out += Suggestion(build(raw, name, response.intent, iconKeys, palette), raw.why.trim().take(MAX_WHY).trim())
        }
        return out
    }

    private fun build(
        raw: SuggestedHabitDto,
        name: String,
        intent: String,
        iconKeys: Collection<String>,
        palette: List<String>,
    ): HabitTemplate {
        val frequency = frequencyOf(raw.frequency)
        val target = raw.targetCount?.takeIf { it in GOAL_RANGE }
        return HabitTemplate(
            name = name,
            icon = raw.iconKey.takeIf { it in iconKeys } ?: iconFor(intent),
            colorHex = palette[raw.colorSeed.coerceIn(0, palette.lastIndex)],
            category = DEFAULT_CATEGORIES.firstOrNull { it.equals(raw.category.trim(), ignoreCase = true) }
                ?: categoryFor(intent),
            frequencyType = frequency.type,
            specificDays = frequency.days,
            targetCount = target,
            unit = if (target == null) null else raw.unit?.trim()?.take(MAX_UNIT)?.trim()?.ifEmpty { null },
            reminderTime = raw.reminderTime?.takeIf { it.length == 5 && runCatching { LocalTime.parse(it, timeFormat) }.isSuccess },
            intervalDays = frequency.interval,
        )
    }

    private class Frequency(val type: FrequencyType, val days: Set<DayOfWeek> = emptySet(), val interval: Int? = null)

    private fun frequencyOf(dto: FrequencyDto): Frequency {
        val daily = Frequency(FrequencyType.DAILY)
        return when (dto.type.uppercase()) {
            "SPECIFIC_DAYS" -> {
                val days = dto.days.orEmpty()
                    .mapNotNull { runCatching { DayOfWeek.valueOf(it.trim().uppercase()) }.getOrNull() }
                    .toSet()
                if (FrequencyValidator.isValid(FrequencyType.SPECIFIC_DAYS, days, null)) {
                    Frequency(FrequencyType.SPECIFIC_DAYS, days)
                } else {
                    daily
                }
            }
            "INTERVAL" -> {
                val step = dto.intervalDays?.takeIf { it in INTERVAL_RANGE }
                if (step != null && FrequencyValidator.isValid(FrequencyType.INTERVAL, emptySet(), step)) {
                    Frequency(FrequencyType.INTERVAL, interval = step)
                } else {
                    daily
                }
            }
            else -> daily
        }
    }

    fun categoryFor(intent: String): String = when (intent) {
        "sleep", "health" -> "Health"
        "fitness" -> "Fitness"
        "focus" -> "Productivity"
        "mind" -> "Mind"
        else -> "General"
    }

    fun iconFor(intent: String): String = when (intent) {
        "sleep" -> "sleep"
        "fitness" -> "fitness_center"
        "focus" -> "phone_off"
        "health" -> "water_drop"
        "mind" -> "self_improvement"
        else -> "star"
    }
}
