package com.habitly.app.ai.openai

import com.habitly.app.ai.AiActionDto
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.CountsDto
import com.habitly.app.ai.FrequencyDto
import com.habitly.app.ai.HabitSummaryDto
import com.habitly.app.ai.ReviewPageDto
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.SuggestedHabitDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCleanerTest {
    private val counts = CountsDto(0, 0, 0, 0)
    private fun summary(id: Long) = HabitSummaryDto(
        id = id, name = "H$id", category = "Health", frequency = FrequencyDto("DAILY"),
        createdDaysAgo = 10, currentStreak = 0, bestStreak = 0, last7 = counts, prev7 = counts,
        weekdays = emptyList(), dueToday = true,
    )

    private val reviewReq = ReviewRequest("2026-09-28", listOf(summary(7)))
    private fun page(kind: String, habitId: Long? = null, action: AiActionDto? = null) = ReviewPageDto(kind, "h", "b", habitId, action)
    private fun act(type: String = "log_done", habitId: Long = 7, label: String = "Do it", time: String? = null) =
        AiActionDto(type, habitId, time = time, label = label)

    @Test fun cutAndWords() {
        assertEquals("abc", OpenAiCleaner.cut("abc", 3))
        assertEquals("ab…", OpenAiCleaner.cut("abcd", 3))
        assertEquals("one two…", OpenAiCleaner.words("one two three", 2))
        assertEquals("one two", OpenAiCleaner.words("  one two ", 5))
    }

    @Test fun reviewNeedsOneLeadingSummaryAndAtLeastThreePages() {
        val bad = listOf(
            listOf(page("summary"), page("tip")),
            listOf(page("win"), page("summary"), page("tip")),
            listOf(page("summary"), page("summary"), page("tip")),
        )
        for (pages in bad) {
            val thrown = runCatching { OpenAiCleaner.review(ReviewResponse(pages), reviewReq) }.exceptionOrNull()
            assertTrue(pages.toString(), thrown is UpstreamException)
        }
        val ok = OpenAiCleaner.review(ReviewResponse(listOf(page("summary"), page("win", 7), page("tip", 7, act()))), reviewReq)
        assertEquals(3, ok.pages.size)
    }

    @Test fun reviewDropsUnknownHabitsAndMismatchedActions() {
        val out = OpenAiCleaner.review(
            ReviewResponse(
                listOf(
                    page("summary"),
                    page("win", habitId = 99),
                    page("tip", habitId = 7, action = act(habitId = 8)),
                    page("tip", habitId = 7, action = act(label = "x".repeat(40))),
                    page("tip", habitId = 7, action = act(type = "set_reminder", time = "7pm")),
                    page("tip", habitId = 7, action = act()),
                ),
            ),
            reviewReq,
        )
        assertEquals(5, out.pages.size) // capped at 5
        assertNull(out.pages[1].habitId)
        assertNull(out.pages[2].action) // action for another habit than the page's
        assertEquals(28, out.pages[3].action!!.label.length)
        assertNull(out.pages[4].action) // bad time
    }

    @Test fun coachKeepsTwoKnownActionsAndCapsTheReply() {
        val req = CoachRequest("2026-10-02", listOf(summary(7)), emptyList())
        val out = OpenAiCleaner.coach(
            CoachResponse("x".repeat(30) + " " + "y".repeat(900), listOf(act(habitId = 99), act(), act(type = "open_habit"), act(type = "rest_today"))),
            req,
        )
        assertEquals(listOf("log_done", "open_habit"), out.actions.map { it.type })
        assertTrue(out.reply.length <= 600)
    }

    @Test fun suggestWithNoUsableHabitsIsAnUpstreamFailure() {
        val req = SuggestRequest("sleep", emptyList(), listOf("star"), listOf("General"))
        val blank = SuggestedHabitDto("  ", "star", 0, "General", FrequencyDto("DAILY"))
        val thrown = runCatching { OpenAiCleaner.suggest(SuggestResponse("sleep", listOf(blank)), req) }.exceptionOrNull()
        assertTrue(thrown is UpstreamException)
    }
}
