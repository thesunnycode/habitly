package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A skipped day is a rest day the user declared. Following Loop Habit Tracker's rule, it leaves the
 * score unchanged and does not break the streak — the point being that people abandon a habit at the
 * moment they see a streak reset, so there has to be a way to miss a day honestly.
 */
class SkipStateTest {

    private fun log(day: Int, status: CompletionStatus) =
        CompletionRecord(LocalDate.of(2026, 8, day), status)

    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun `a skipped day does not break the streak`() {
        val records = listOf(
            log(20, CompletionStatus.DONE),
            log(21, CompletionStatus.SKIPPED),
            log(22, CompletionStatus.DONE),
            log(23, CompletionStatus.DONE),
        )
        assertEquals(3, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `a skipped day does not count towards the streak either`() {
        val records = listOf(
            log(22, CompletionStatus.SKIPPED),
            log(23, CompletionStatus.SKIPPED),
        )
        assertEquals(0, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `an explicitly missed day breaks the streak`() {
        val records = listOf(
            log(21, CompletionStatus.DONE),
            log(22, CompletionStatus.MISSED),
            log(23, CompletionStatus.DONE),
        )
        assertEquals(1, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `skipped days are excluded from the completion rate denominator`() {
        // Four due days, one of them skipped, the other three kept: 100%, not 75%.
        val records = listOf(
            log(20, CompletionStatus.DONE),
            log(21, CompletionStatus.SKIPPED),
            log(22, CompletionStatus.DONE),
            log(23, CompletionStatus.DONE),
        )
        assertEquals(1f, StreakCalculator.completionRate(records, today), 0.001f)
    }

    @Test
    fun `a missed day still counts against the completion rate`() {
        val records = listOf(
            log(20, CompletionStatus.DONE),
            log(21, CompletionStatus.MISSED),
            log(22, CompletionStatus.DONE),
            log(23, CompletionStatus.DONE),
        )
        assertEquals(0.75f, StreakCalculator.completionRate(records, today), 0.001f)
    }

    @Test
    fun `a skipped day is not treated as completed`() {
        val record = log(23, CompletionStatus.SKIPPED)
        assertEquals(false, record.completed)
    }

    @Test
    fun `the boolean constructor still maps true to done and false to missed`() {
        assertEquals(CompletionStatus.DONE, CompletionRecord(today, true).status)
        assertEquals(CompletionStatus.MISSED, CompletionRecord(today, false).status)
    }

    @Test
    fun `lastBrokenDueDay names the scheduled day the streak died on`() {
        // The 22nd was missed, so that is the day worth offering to convert into a rest day.
        val records = listOf(
            log(20, CompletionStatus.DONE),
            log(21, CompletionStatus.DONE),
            log(22, CompletionStatus.MISSED),
        )
        assertEquals(
            LocalDate.of(2026, 8, 22),
            StreakCalculator.lastBrokenDueDay(records, today),
        )
    }

    @Test
    fun `lastBrokenDueDay finds a day that was never logged at all`() {
        // Nothing recorded for the 22nd; it still elapsed, so it is what broke the run.
        val records = listOf(log(20, CompletionStatus.DONE), log(21, CompletionStatus.DONE))
        assertEquals(
            LocalDate.of(2026, 8, 22),
            StreakCalculator.lastBrokenDueDay(records, today),
        )
    }

    @Test
    fun `lastBrokenDueDay is null while the streak is intact`() {
        val records = listOf(
            log(21, CompletionStatus.DONE),
            log(22, CompletionStatus.DONE),
            log(23, CompletionStatus.DONE),
        )
        assertEquals(null, StreakCalculator.lastBrokenDueDay(records, today))
    }
}
