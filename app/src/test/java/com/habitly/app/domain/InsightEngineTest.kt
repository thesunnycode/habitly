package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The engine's job is to say something true and specific, or say nothing. Every template therefore
 * gets two tests: one proving it fires on data that supports it, and one proving it stays silent on
 * data that does not.
 */
class InsightEngineTest {

    // Monday 3 Aug 2026 .. so dates below are easy to reason about by weekday.
    private val today = LocalDate.of(2026, 8, 24) // a Monday

    private fun history(
        name: String = "Read",
        done: List<LocalDate> = emptyList(),
        missed: List<LocalDate> = emptyList(),
        currentStreak: Int = 0,
        bestStreak: Int = 0,
    ) = HabitHistory(
        name = name,
        records = done.map { CompletionRecord(it, CompletionStatus.DONE) } +
            missed.map { CompletionRecord(it, CompletionStatus.MISSED) },
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        schedule = HabitSchedule.DAILY,
    )

    private fun days(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    private fun kinds(vararg h: HabitHistory) =
        InsightEngine.insights(h.toList(), today, limit = 10).map { it.kind }

    // ---------- personal best ----------

    @Test
    fun `a run longer than the previous best is reported as a personal best`() {
        // Five in a row to today, after an earlier best of three.
        val earlier = days(LocalDate.of(2026, 8, 4), 3)
        val current = days(today.minusDays(4), 5)
        val h = history(done = earlier + current, currentStreak = 5, bestStreak = 5)
        val insight = InsightEngine.insights(listOf(h), today).first { it.kind == InsightKind.PERSONAL_BEST }
        assertTrue(insight.text, insight.text.contains("5"))
        assertTrue(insight.text, insight.text.contains("3"))
    }

    @Test
    fun `a run shorter than three days is not called a personal best`() {
        val h = history(done = days(today.minusDays(1), 2), currentStreak = 2, bestStreak = 2)
        assertTrue(InsightKind.PERSONAL_BEST !in kinds(h))
    }

    @Test
    fun `matching an old best without beating it is not a personal best`() {
        val earlier = days(LocalDate.of(2026, 8, 4), 4)
        val current = days(today.minusDays(3), 4)
        val h = history(done = earlier + current, currentStreak = 4, bestStreak = 4)
        assertTrue(InsightKind.PERSONAL_BEST !in kinds(h))
    }

    // ---------- recovery ----------

    @Test
    fun `getting straight back on it after one miss is reported`() {
        val h = history(
            done = days(today.minusDays(2), 3),
            missed = listOf(today.minusDays(3)),
            currentStreak = 3,
            bestStreak = 8,
        )
        val insight = InsightEngine.insights(listOf(h), today).first { it.kind == InsightKind.RECOVERY }
        assertTrue(insight.text, insight.text.contains("Friday"))
    }

    @Test
    fun `a miss with only one day back is not yet a recovery`() {
        val h = history(
            done = listOf(today),
            missed = listOf(today.minusDays(1)),
            currentStreak = 1,
            bestStreak = 8,
        )
        assertTrue(InsightKind.RECOVERY !in kinds(h))
    }

    // ---------- slipping habit ----------

    @Test
    fun `a habit whose rate has dropped sharply is named`() {
        // Kept every day of the older fortnight, only two days of the recent one.
        val older = days(today.minusDays(27), 14)
        val recent = days(today.minusDays(13), 2)
        val h = history(name = "Reading", done = older + recent, currentStreak = 0, bestStreak = 14)
        val insight = InsightEngine.insights(listOf(h), today).first { it.kind == InsightKind.HABIT_SLIPPING }
        assertTrue(insight.text, insight.text.contains("Reading"))
    }

    @Test
    fun `a steady habit is not reported as slipping`() {
        val h = history(done = days(today.minusDays(27), 28), currentStreak = 28, bestStreak = 28)
        assertTrue(InsightKind.HABIT_SLIPPING !in kinds(h))
    }

    // ---------- weekday patterns ----------

    @Test
    fun `a consistently missed weekday is called out by name`() {
        // Four Thursdays missed, everything else kept, over four weeks.
        val all = days(today.minusDays(27), 28)
        val thursdays = all.filter { it.dayOfWeek == java.time.DayOfWeek.THURSDAY }
        val h = history(
            done = all - thursdays.toSet(),
            missed = thursdays,
            currentStreak = 3,
            bestStreak = 6,
        )
        val insight = InsightEngine.insights(listOf(h), today, limit = 10)
            .first { it.kind == InsightKind.WEAK_WEEKDAY }
        assertTrue(insight.text, insight.text.contains("Thursday"))
    }

    @Test
    fun `a weekday seen fewer than four times is never called out`() {
        // Only ten days of history, so no weekday has enough occurrences to judge.
        val all = days(today.minusDays(9), 10)
        val thursdays = all.filter { it.dayOfWeek == java.time.DayOfWeek.THURSDAY }
        val h = history(done = all - thursdays.toSet(), missed = thursdays, currentStreak = 3)
        assertTrue(InsightKind.WEAK_WEEKDAY !in kinds(h))
    }

    // ---------- limits and ordering ----------

    @Test
    fun `at most three insights are returned by default`() {
        val all = days(today.minusDays(27), 28)
        val thursdays = all.filter { it.dayOfWeek == java.time.DayOfWeek.THURSDAY }
        val a = history(name = "Water", done = all - thursdays.toSet(), missed = thursdays, currentStreak = 3, bestStreak = 3)
        val b = history(name = "Reading", done = days(today.minusDays(27), 14), currentStreak = 0, bestStreak = 14)
        assertTrue(InsightEngine.insights(listOf(a, b), today).size <= 3)
    }

    @Test
    fun `a personal best outranks a weak weekday`() {
        val all = days(today.minusDays(27), 28)
        val thursdays = all.filter { it.dayOfWeek == java.time.DayOfWeek.THURSDAY }
        val earlier = days(LocalDate.of(2026, 7, 1), 3)
        val h = history(
            done = earlier + (all - thursdays.toSet()),
            missed = thursdays,
            currentStreak = 4,
            bestStreak = 4,
        )
        val ordered = InsightEngine.insights(listOf(h), today, limit = 10).map { it.kind }
        assertTrue(
            ordered.toString(),
            ordered.indexOf(InsightKind.PERSONAL_BEST) < ordered.indexOf(InsightKind.WEAK_WEEKDAY),
        )
    }

    @Test
    fun `no history produces no insights rather than an empty-sounding one`() {
        assertEquals(emptyList<Insight>(), InsightEngine.insights(emptyList(), today))
    }

    @Test
    fun `a single day of history produces no insights`() {
        val h = history(done = listOf(today), currentStreak = 1, bestStreak = 1)
        assertEquals(emptyList<Insight>(), InsightEngine.insights(listOf(h), today))
    }

    // ---------- rest-positive ----------

    @Test
    fun `a long unbroken run suggests a rest day rather than more effort`() {
        val h = history(done = days(today.minusDays(20), 21), currentStreak = 21, bestStreak = 21)
        assertTrue(InsightKind.REST_POSITIVE in kinds(h))
    }

    @Test
    fun `a short run does not suggest resting`() {
        val h = history(done = days(today.minusDays(4), 5), currentStreak = 5, bestStreak = 5)
        assertTrue(InsightKind.REST_POSITIVE !in kinds(h))
    }
}
