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
            SLEEP.containsMatchIn(text) -> "sleep"
            FITNESS.containsMatchIn(text) -> "fitness"
            FOCUS.containsMatchIn(text) -> "focus"
            HEALTH.containsMatchIn(text) -> "health"
            MIND.containsMatchIn(text) -> "mind"
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
        val summary = when {
            due == 0 -> ReviewPageDto("summary", "A quiet week", "Nothing was scheduled in the last 7 days. Add or schedule a habit to get rolling.")
            done == 0 -> ReviewPageDto("summary", "A fresh start awaits", "You completed 0 of $due planned check-ins. One small win today restarts the week.")
            pct >= 50 -> ReviewPageDto("summary", "Solid week — $pct% kept", "You completed $done of $due planned check-ins across ${habits.size} habits.")
            else -> ReviewPageDto("summary", "$pct% kept — room to grow", "You completed $done of $due planned check-ins. Small, steady steps add up.")
        }
        val streakHabit = habits.maxByOrNull { it.currentStreak }?.takeIf { it.currentStreak > 0 }
        val win = streakHabit?.let {
            ReviewPageDto(
                "win", "${it.name}: ${it.currentStreak}-day streak",
                "Your longest running streak right now. Keep it going tomorrow.", habitId = it.id,
            )
        } ?: ReviewPageDto("win", "Every streak starts with one day", "No streak is running yet. Today is a good day to begin one.")
        val pattern = weakestDay(habits)?.let { day ->
            ReviewPageDto(
                "pattern", "${dayName(day)}s are your toughest day",
                "You complete the fewest habits on ${dayName(day)}s. Plan something lighter for them.",
            )
        } ?: ReviewPageDto("pattern", "Consistency beats intensity", "Not enough history for a weekday pattern yet. Keep logging and it will show up.")
        val tip = habits.filter { it.last7.missed > 0 }.maxByOrNull { it.last7.missed }?.let {
            val time = "%02d:00".format(it.avgCheckInHour ?: 19)
            ReviewPageDto(
                "tip", "Nudge ${it.name}",
                "You missed it ${it.last7.missed} times this week. A reminder at $time fits when you usually check in.",
                habitId = it.id,
                action = AiActionDto("set_reminder", it.id, time = time, label = "Move reminder"),
            )
        } ?: ReviewPageDto("tip", "Keep it small", "On a busy day, a tiny version of a habit still keeps the chain alive.")
        return AiResult.Success(ReviewResponse(listOf(summary, win, pattern, tip)), null)
    }

    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> {
        delay(delayMs)
        val habits = request.habits
        val open = habits.filter { it.dueToday && it.todayStatus != "DONE" && it.todayStatus != "SKIPPED" }
        val riskiest = open.maxByOrNull { it.risk ?: (it.last7.missed / 7.0) }
        val userText = request.messages.lastOrNull { it.role == "user" }?.text?.trim()
        val left = "${open.size} ${if (open.size == 1) "habit" else "habits"} left today"
        val reply = when {
            habits.isEmpty() -> CoachResponse("Add a habit to get started — I can help you plan it from there.")
            userText == null && riskiest == null -> CoachResponse("Everything's done today — nice work.")
            userText == null -> CoachResponse(
                "You have $left. ${riskiest!!.name} is the one to watch — a short version still counts.",
                actions(riskiest),
            )
            else -> when (userText.lowercase()) {
                "plan my day" -> if (riskiest == null) {
                    CoachResponse("Nothing left to plan — today is done. Rest up and come back tomorrow.")
                } else {
                    CoachResponse(
                        "You have $left. Start with ${riskiest.name}, then clear the rest: ${open.joinToString { it.name }}.",
                        actions(riskiest),
                    )
                }
                "why do i slip?" -> {
                    val slipper = habits.maxByOrNull { it.last7.missed }?.takeIf { it.last7.missed > 0 }
                    val day = weakestDay(habits)?.let { dayName(it) + "s" }
                    CoachResponse(
                        if (slipper == null) {
                            "Nothing is slipping this week — keep doing what you are doing."
                        } else {
                            "Your misses cluster on ${day ?: "busy days"}, and ${slipper.name} slipped ${slipper.last7.missed} time(s) this week. " +
                                "Making it smaller on those days helps."
                        },
                    )
                }
                "motivate me" -> {
                    val best = habits.maxByOrNull { it.currentStreak }
                    CoachResponse(
                        when {
                            best != null && best.currentStreak > 0 ->
                                "${best.name} is at a ${best.currentStreak}-day streak. You already show up — do one more today."
                            riskiest != null -> "Every streak starts with one day. Pick ${riskiest.name} and start today."
                            else -> "You have done everything today. That is how streaks get built."
                        },
                    )
                }
                "which habit first?" -> if (riskiest == null) {
                    CoachResponse("Nothing left today — everything is already done.")
                } else {
                    CoachResponse(
                        "${riskiest.name}. It is the one most likely to slip, so do it while motivation is high.",
                        actions(riskiest),
                    )
                }
                else -> CoachResponse(
                    if (riskiest == null) "Good question. Everything is done today, so you can take a breather."
                    else "Good question. With $left, a small win on ${riskiest.name} is a great place to start.",
                )
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
        val SLEEP = Regex("""\b(sleep\w*|bed|tired)\b""")
        val FITNESS = Regex("""\b(fit|fitness|gym|run|running|workout\w*|exercis\w*)\b""")
        val FOCUS = Regex("""\b(phone|screen\w*|scroll\w*|focus\w*)\b""")
        val HEALTH = Regex("""\b(water|drink\w*|eat|eating|health\w*)\b""")
        val MIND = Regex("""\b(calm|stress\w*|meditat\w*|mind\w*)\b""")
    }
}
