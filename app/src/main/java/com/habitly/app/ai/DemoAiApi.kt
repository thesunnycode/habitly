package com.habitly.app.ai

import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Canned, offline answers built only from the request, so the AI screens work with no proxy. */
class DemoAiApi(private val delayMs: Long = 700) : AiApi {

    override suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse> {
        delay(delayMs)
        val text = request.text.lowercase()
        val intent = when {
            SLEEP.any { it in text } -> "sleep"
            FITNESS.any { it in text } -> "fitness"
            FOCUS.any { it in text } -> "focus"
            HEALTH.any { it in text } -> "health"
            MIND.any { it in text } -> "mind"
            else -> "other"
        }
        val existing = request.existingHabitNames.map { it.lowercase() }.toSet()
        val habits = when (intent) {
            "sleep" -> listOf(
                habit("Lights out by 23:00", "sleep", 2, "Health", "22:45", "A fixed bedtime is the easiest sleep win"),
                habit("No phone in bed", "phone_off", 9, "Health", "22:30", "Screens keep your brain awake"),
                habit("Wind down with a book", "menu_book", 5, "Mind", "22:00", "Reading signals your body it is time to rest"),
            )
            "fitness" -> listOf(
                habit("Move for 20 minutes", "fitness_center", 1, "Fitness", "07:00", "A short session is easier to repeat", 20, "minutes"),
                habit("Evening walk", "walk", 4, "Fitness", "18:30", "Low effort, and it clears your head"),
                habit("Stretch after waking", "run", 7, "Fitness", "07:30", "Five minutes keeps you loose and consistent"),
            )
            "focus" -> listOf(
                habit("Phone away for focus", "phone_off", 9, "Productivity", "09:00", "Out of sight is out of mind"),
                habit("One deep-work block", "star", 3, "Productivity", "09:30", "One protected hour beats a scattered day"),
            )
            "health" -> listOf(
                habit("Drink 8 cups of water", "water_drop", 6, "Health", "10:00", "Easy to count and easy to win", 8, "cups"),
                habit("Eat a real breakfast", "food", 8, "Health", "08:00", "A steady start avoids energy crashes"),
                habit("Take a daily walk", "walk", 4, "Health", "17:30", "Small, regular movement adds up"),
            )
            "mind" -> listOf(
                habit("Meditate for 5 minutes", "self_improvement", 10, "Mind", "07:15", "Short and regular beats long and rare", 5, "minutes"),
                habit("Evening gratitude note", "spa", 0, "Mind", "21:00", "Ending the day on a good note lowers stress"),
            )
            else -> listOf(
                habit("Read 10 pages", "menu_book", 5, "Learning", "21:00", "A tiny target keeps the streak alive", 10, "pages"),
                habit("Plan tomorrow", "star", 3, "Productivity", "20:30", "Two minutes tonight saves a messy morning"),
            )
        }.filter { it.name.lowercase() !in existing }
        return AiResult.Success(SuggestResponse(intent, habits), null)
    }

    override suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse> {
        delay(delayMs)
        val habits = request.habits
        val done = habits.sumOf { it.last7.done }
        val due = habits.sumOf { it.last7.due }
        val pct = if (due == 0) 0 else (done * 100.0 / due).toInt()
        val pages = mutableListOf(
            ReviewPageDto("summary", "Solid week — $pct% kept", "You completed $done of $due planned check-ins across ${habits.size} habits."),
        )
        habits.maxByOrNull { it.currentStreak }?.takeIf { it.currentStreak > 0 }?.let {
            pages += ReviewPageDto(
                "win", "${it.name}: ${it.currentStreak}-day streak",
                "Your longest running streak right now. Keep it going tomorrow.", habitId = it.id,
            )
        }
        weakestDay(habits)?.let { day ->
            pages += ReviewPageDto(
                "pattern", "${dayName(day)}s are your toughest day",
                "You complete the fewest habits on ${dayName(day)}s. Plan something lighter for them.",
            )
        }
        habits.filter { it.last7.missed > 0 }.maxByOrNull { it.last7.missed }?.let {
            val time = "%02d:00".format(it.avgCheckInHour ?: 19)
            pages += ReviewPageDto(
                "tip", "Nudge ${it.name}",
                "You missed it ${it.last7.missed} times this week. A reminder at $time fits when you usually check in.",
                habitId = it.id,
                action = AiActionDto("set_reminder", it.id, time = time, label = "Move reminder"),
            )
        }
        return AiResult.Success(ReviewResponse(pages.take(4)), null)
    }

    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> {
        delay(delayMs)
        val open = request.habits.filter { it.dueToday && it.todayStatus != "DONE" && it.todayStatus != "SKIPPED" }
        val riskiest = open.maxByOrNull { it.risk ?: (it.last7.missed / 7.0) }
        val userText = request.messages.lastOrNull { it.role == "user" }?.text
        val reply = when {
            riskiest == null -> CoachResponse("Everything's done today — nice work.")
            userText == null -> CoachResponse(
                "You have ${open.size} habits left today. ${riskiest.name} is the one to watch — a short version still counts.",
                actions(riskiest),
            )
            else -> when (userText.trim().lowercase()) {
                "plan my day" -> CoachResponse(
                    "${open.size} left today. Start with ${riskiest.name}, then clear the rest: ${open.joinToString { it.name }}.",
                    actions(riskiest),
                )
                "why do i slip?" -> {
                    val day = weakestDay(request.habits)?.let { dayName(it) + "s" } ?: "busy days"
                    CoachResponse(
                        "Your misses cluster on $day, and ${riskiest.name} slipped ${riskiest.last7.missed} time(s) this week. " +
                            "Making it smaller on those days helps.",
                    )
                }
                "motivate me" -> {
                    val best = request.habits.maxByOrNull { it.currentStreak }
                    CoachResponse(
                        if (best != null && best.currentStreak > 0) {
                            "${best.name} is at a ${best.currentStreak}-day streak. You already show up — do one more today."
                        } else {
                            "Every streak starts with one day. Pick ${riskiest.name} and start today."
                        },
                    )
                }
                "which habit first?" -> CoachResponse(
                    "${riskiest.name}. It is the one most likely to slip, so do it while motivation is high.",
                    actions(riskiest),
                )
                else -> CoachResponse("Good question. With ${open.size} left today, a small win on ${riskiest.name} is a great place to start.")
            }
        }
        return AiResult.Success(reply, null)
    }

    private fun actions(h: HabitSummaryDto) = listOf(
        AiActionDto("log_done", h.id, label = "Log it now"),
        AiActionDto("rest_today", h.id, label = "Rest day"),
    )

    /** The weekday with the lowest done/due ratio across all habits, or null with no due days. */
    private fun weakestDay(habits: List<HabitSummaryDto>): String? =
        DayOfWeek.entries.map { it.name }
            .mapNotNull { day ->
                val stats = habits.flatMap { h -> h.weekdays.filter { it.day == day } }
                val due = stats.sumOf { it.due }
                if (due == 0) null else day to stats.sumOf { it.done }.toDouble() / due
            }
            .minByOrNull { it.second }?.first

    private fun dayName(day: String) = DayOfWeek.valueOf(day).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun habit(
        name: String, icon: String, seed: Int, category: String, reminder: String, why: String,
        target: Int? = null, unit: String? = null,
    ) = SuggestedHabitDto(
        name = name, iconKey = icon, colorSeed = seed, category = category,
        frequency = FrequencyDto("DAILY"), targetCount = target, unit = unit, reminderTime = reminder, why = why,
    )

    private companion object {
        val SLEEP = listOf("sleep", "bed", "tired")
        val FITNESS = listOf("fit", "gym", "run", "workout", "exercise")
        val FOCUS = listOf("phone", "screen", "scroll", "focus")
        val HEALTH = listOf("water", "drink", "eat", "health")
        val MIND = listOf("calm", "stress", "meditat", "mind")
    }
}
