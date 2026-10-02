package com.habitly.app.ai.openai

import com.habitly.app.ai.AiActionDto
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.FrequencyDto
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse

/** The model answered, but not in a usable shape; worth one retry. */
internal class UpstreamException(message: String) : Exception(message)

/**
 * Trims and checks the model's structured output before the app sees it — the same rules the retired
 * proxy applied. The feature screens validate again against live data; this layer only enforces shape.
 */
internal object OpenAiCleaner {
    const val SAFE_REPLY =
        "I can't help with that one, but I'm here for your habits and routines. If something feels heavy, please talk to someone you trust or a local helpline."

    private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private const val MAX_REPLY_CHARS = 600

    fun cut(s: String, n: Int): String = if (s.length <= n) s else s.take(n - 1).trimEnd() + "…"

    fun words(s: String, n: Int): String {
        val w = s.trim().split(Regex("\\s+"))
        return if (w.size <= n) s.trim() else w.take(n).joinToString(" ") + "…"
    }

    private fun validAction(a: AiActionDto): Boolean = when (a.type) {
        "set_reminder" -> a.time != null && HHMM.matches(a.time)
        "set_days" -> !a.days.isNullOrEmpty()
        "set_rest_day" -> a.date != null && YMD.matches(a.date)
        "set_goal" -> a.count != null && a.count in 1..999
        else -> true
    }

    private fun cleanAction(a: AiActionDto?, ids: Set<Long>, pageHabitId: Long? = null): AiActionDto? =
        a?.takeIf { it.habitId in ids && (pageHabitId == null || it.habitId == pageHabitId) && validAction(it) }
            ?.copy(label = cut(a.label, 28))

    private fun cleanFrequency(f: FrequencyDto): FrequencyDto {
        val daily = FrequencyDto("DAILY", null, null)
        return when (f.type) {
            "SPECIFIC_DAYS" -> if (!f.days.isNullOrEmpty()) FrequencyDto(f.type, f.days, null) else daily
            "INTERVAL" -> if (f.intervalDays != null && f.intervalDays in 2..30) FrequencyDto(f.type, null, f.intervalDays) else daily
            else -> daily
        }
    }

    fun suggest(out: SuggestResponse, input: SuggestRequest): SuggestResponse {
        fun icon(k: String) = if (k in input.iconKeys) k else if ("star" in input.iconKeys) "star" else input.iconKeys.firstOrNull() ?: k
        fun category(c: String) = if (c in input.categories) c else if ("General" in input.categories) "General" else input.categories.firstOrNull() ?: c
        val habits = out.habits
            .filter { it.name.isNotBlank() }
            .take(4)
            .map { h ->
                val countable = h.targetCount != null && h.targetCount in 2..999
                h.copy(
                    name = cut(h.name.trim(), 40), why = cut(h.why, 90),
                    iconKey = icon(h.iconKey), category = category(h.category),
                    colorSeed = h.colorSeed.coerceIn(0, 11),
                    reminderTime = h.reminderTime?.takeIf { HHMM.matches(it) },
                    frequency = cleanFrequency(h.frequency),
                    targetCount = if (countable) h.targetCount else null,
                    unit = if (countable) h.unit else null,
                )
            }
        if (habits.isEmpty()) throw UpstreamException("no habits")
        return SuggestResponse(out.intent, habits)
    }

    fun review(out: ReviewResponse, input: ReviewRequest): ReviewResponse {
        val ids = input.habits.map { it.id }.toSet()
        val pages = out.pages.take(5).map { p ->
            val habitId = p.habitId?.takeIf { it in ids }
            p.copy(headline = cut(p.headline, 60), body = cut(p.body, 160), habitId = habitId, action = cleanAction(p.action, ids, habitId))
        }
        if (pages.size < 3 || pages.first().kind != "summary" || pages.count { it.kind == "summary" } != 1) {
            throw UpstreamException("bad review shape")
        }
        return ReviewResponse(pages)
    }

    fun coach(out: CoachResponse, input: CoachRequest): CoachResponse {
        val ids = input.habits.map { it.id }.toSet()
        val actions = out.actions.mapNotNull { cleanAction(it, ids) }.take(2)
        // Capped in characters too: the app sends this reply back as history, limited to 600.
        return CoachResponse(words(out.reply, 80).take(MAX_REPLY_CHARS), actions)
    }
}
