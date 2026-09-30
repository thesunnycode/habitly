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

    @Test fun coachAnswersEachChipDistinctly() = runTest {
        val habits = listOf(summary(1, "Read", streak = 3, missed = 2, risk = 0.5))
        suspend fun reply(text: String) =
            api.coach(CoachRequest("2026-09-30", habits, listOf(ChatMessageDto("user", text)))).value().reply
        assertTrue(reply("Plan my day").contains("Start with Read"))
        assertTrue(reply("Why do I slip?").contains("misses cluster"))
        assertTrue(reply("Motivate me").contains("3-day streak"))
        assertTrue(reply("Which habit first?").contains("most likely to slip"))
        assertTrue(reply("anything else").startsWith("Good question"))
    }

    @Test fun coachPluralisesAndHandlesEdgeCases() = runTest {
        val one = api.coach(CoachRequest("2026-09-30", listOf(summary(1, "Read")), emptyList())).value()
        assertTrue(one.reply.contains("1 habit left today"))
        val none = api.coach(CoachRequest("2026-09-30", emptyList(), emptyList())).value()
        assertTrue(none.reply.startsWith("Add a habit"))
        // Everything done but the user still asked something: answer it rather than repeating "all done".
        val done = listOf(summary(1, "Read", streak = 2, today = "DONE"))
        val r = api.coach(CoachRequest("2026-09-30", done, listOf(ChatMessageDto("user", "Motivate me")))).value()
        assertTrue(r.reply.contains("2-day streak"))
        assertTrue(r.actions.isEmpty())
    }

    @Test fun reviewAlwaysHasThreeOrFourTruthfulPages() = runTest {
        val quiet = api.weeklyReview(ReviewRequest("2026-09-28", emptyList())).value().pages
        assertTrue(quiet.size in 3..4)
        assertEquals("A quiet week", quiet.first().headline)
        val zero = listOf(summary(1, "Read", done = 0, missed = 0))
        val pages = api.weeklyReview(ReviewRequest("2026-09-28", zero)).value().pages
        assertTrue(pages.size in 3..4)
        assertFalse(pages.any { it.headline.contains("0% kept") })
    }

    @Test fun intentMatchingUsesWholeWords() = runTest {
        suspend fun intent(t: String) = api.suggestHabits(SuggestRequest(t, emptyList(), HABIT_ICON_KEYS, emptyList())).value().intent
        assertEquals("other", intent("remind me to do great things"))
        assertEquals("health", intent("I want to eat better"))
        assertEquals("fitness", intent("I am running more"))
    }
}
