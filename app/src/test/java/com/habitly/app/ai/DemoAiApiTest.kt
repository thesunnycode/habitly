package com.habitly.app.ai

import com.habitly.app.ui.components.HABIT_ICON_KEYS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAiApiTest {
    private val api = DemoAiApi(delayMs = 0)

    private fun summary(
        id: Long, name: String, streak: Int = 0, missed: Int = 0, done: Int = 5,
        risk: Double? = null, today: String? = null,
    ) = HabitSummaryDto(
        id = id, name = name, category = "Health", frequency = FrequencyDto("DAILY"),
        createdDaysAgo = 30, currentStreak = streak, bestStreak = streak,
        last7 = CountsDto(done, 0, missed, 7), prev7 = CountsDto(3, 0, 4, 7),
        weekdays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
            .map { WeekdayStatDto(it, if (it == "TUESDAY") 1 else 3, 4) },
        dueToday = true, todayStatus = today, risk = risk,
    )

    private fun <T> AiResult<T>.value(): T = (this as AiResult.Success).value

    @Test fun suggestSleepUsesKnownIcons() = runTest {
        val r = api.suggestHabits(SuggestRequest("I want to sleep better", emptyList(), HABIT_ICON_KEYS, emptyList())).value()
        assertEquals("sleep", r.intent)
        assertTrue(r.habits.size >= 2)
        assertTrue(r.habits.all { it.iconKey in HABIT_ICON_KEYS })
    }

    @Test fun suggestSkipsExistingNamesAndAllIconsAreKnown() = runTest {
        for (text in listOf("sleep", "gym", "phone", "water", "calm", "something else")) {
            val r = api.suggestHabits(SuggestRequest(text, listOf("no phone in bed"), HABIT_ICON_KEYS, emptyList())).value()
            assertTrue(text, r.habits.isNotEmpty() && r.habits.all { it.iconKey in HABIT_ICON_KEYS })
            assertFalse(r.habits.any { it.name.equals("No phone in bed", ignoreCase = true) })
        }
    }

    @Test fun reviewStartsWithSummaryAndUsesOnlyKnownIds() = runTest {
        val habits = listOf(summary(1, "Read", streak = 4, missed = 0), summary(2, "Run", streak = 1, missed = 3))
        val pages = api.weeklyReview(ReviewRequest("2026-09-28", habits)).value().pages
        assertEquals("summary", pages.first().kind)
        assertTrue(pages.size in 3..4)
        assertTrue(pages.mapNotNull { it.habitId }.all { it in setOf(1L, 2L) })
        assertEquals(1L, pages.first { it.kind == "win" }.habitId)
        assertEquals("set_reminder", pages.first { it.kind == "tip" }.action?.type)
        assertTrue(pages.first { it.kind == "pattern" }.headline.startsWith("Tuesdays"))
    }

    @Test fun coachOpeningReferencesRiskiestHabit() = runTest {
        val habits = listOf(summary(1, "Read", risk = 0.2), summary(2, "Run", risk = 0.9))
        val r = api.coach(CoachRequest("2026-09-30", habits, emptyList())).value()
        assertTrue(r.reply.contains("Run"))
        assertEquals(listOf("log_done", "rest_today"), r.actions.map { it.type })
        assertTrue(r.actions.all { it.habitId == 2L })
    }

    @Test fun coachAllDoneHasNoActions() = runTest {
        val r = api.coach(CoachRequest("2026-09-30", listOf(summary(1, "Read", today = "DONE")), emptyList())).value()
        assertTrue(r.actions.isEmpty())
        assertTrue(r.reply.startsWith("Everything"))
    }

    @Test fun coachAnswersChipsAndFreeText() = runTest {
        val habits = listOf(summary(1, "Read", risk = 0.5))
        for (chip in listOf("Plan my day", "Why do I slip?", "Motivate me", "Which habit first?", "anything else")) {
            val r = api.coach(CoachRequest("2026-09-30", habits, listOf(ChatMessageDto("user", chip)))).value()
            assertTrue(chip, r.reply.isNotBlank())
        }
    }
}
