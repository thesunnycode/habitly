package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakCalculatorTest {

    private fun record(day: Int, completed: Boolean) =
        CompletionRecord(LocalDate.of(2026, 8, day), completed)

    @Test
    fun `current streak counts consecutive completed days ending today`() {
        val records = listOf(
            record(20, true), record(21, true), record(22, true)
        )
        val today = LocalDate.of(2026, 8, 22)
        assertEquals(3, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `current streak is zero when today is not completed`() {
        val records = listOf(record(20, true), record(21, true), record(22, false))
        val today = LocalDate.of(2026, 8, 22)
        assertEquals(0, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `current streak stops at first gap`() {
        val records = listOf(record(19, true), record(21, true), record(22, true))
        val today = LocalDate.of(2026, 8, 22)
        assertEquals(2, StreakCalculator.currentStreak(records, today))
    }

    @Test
    fun `best streak finds longest run anywhere in history`() {
        val records = listOf(
            record(1, true), record(2, true), record(3, true), record(4, true),
            record(5, false),
            record(6, true), record(7, true)
        )
        assertEquals(4, StreakCalculator.bestStreak(records))
    }

    @Test
    fun `completion rate divides completed by total records`() {
        val records = listOf(record(1, true), record(2, false), record(3, true), record(4, true))
        assertEquals(0.75f, StreakCalculator.completionRate(records), 0.001f)
    }

    @Test
    fun `completion rate is zero for empty history`() {
        assertEquals(0f, StreakCalculator.completionRate(emptyList()), 0.001f)
    }

    @Test
    fun `completion rate counts days with no record at all as missed`() {
        // Logged the 1st and 4th only; the 2nd and 3rd were never opened, but they still elapsed.
        val records = listOf(record(1, true), record(4, true))
        val rate = StreakCalculator.completionRate(records, today = LocalDate.of(2026, 8, 4))
        assertEquals(0.5f, rate, 0.001f)
    }

    @Test
    fun `completion rate over elapsed days ignores records after today`() {
        val records = listOf(record(1, true), record(2, true), record(9, true))
        val rate = StreakCalculator.completionRate(records, today = LocalDate.of(2026, 8, 2))
        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `completion rate over elapsed days is zero for empty history`() {
        assertEquals(0f, StreakCalculator.completionRate(emptyList(), LocalDate.of(2026, 8, 4)), 0.001f)
    }

    // --- schedule-aware maths -------------------------------------------------

    private val monWedFri = HabitSchedule(
        type = FrequencyType.SPECIFIC_DAYS,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    )

    @Test
    fun `streak on a specific-days habit ignores the days it was never due`() {
        // Mon 17, Wed 19, Fri 21 Aug 2026 all done; Tue/Thu were never scheduled.
        val records = listOf(record(17, true), record(19, true), record(21, true))
        val streak = StreakCalculator.currentStreak(records, LocalDate.of(2026, 8, 21), monWedFri)
        assertEquals(3, streak)
    }

    @Test
    fun `streak on a specific-days habit breaks on a missed scheduled day`() {
        val records = listOf(record(17, true), record(21, true))
        val streak = StreakCalculator.currentStreak(records, LocalDate.of(2026, 8, 21), monWedFri)
        assertEquals(1, streak)
    }

    @Test
    fun `an unlogged today does not zero a live streak`() {
        // Sun 23 Aug has no record yet — the day is not over, so the streak still stands at 2.
        val records = listOf(record(21, true), record(22, true))
        val streak = StreakCalculator.currentStreak(records, LocalDate.of(2026, 8, 23))
        assertEquals(2, streak)
    }

    @Test
    fun `an explicit miss today does zero the streak`() {
        val records = listOf(record(21, true), record(22, true), record(23, false))
        val streak = StreakCalculator.currentStreak(records, LocalDate.of(2026, 8, 23))
        assertEquals(0, streak)
    }

    @Test
    fun `completion rate on a specific-days habit divides by scheduled days only`() {
        // Every scheduled day in the window was kept, so the habit is at 100%, not 3/7.
        val records = listOf(record(17, true), record(19, true), record(21, true))
        val rate = StreakCalculator.completionRate(records, LocalDate.of(2026, 8, 23), monWedFri)
        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `completion rate on an interval habit divides by interval days only`() {
        val schedule = HabitSchedule(
            type = FrequencyType.INTERVAL,
            intervalDays = 3,
            startDate = LocalDate.of(2026, 8, 10),
        )
        // Due on the 10th, 13th and 16th; two of the three were kept.
        val records = listOf(record(10, true), record(13, true))
        val rate = StreakCalculator.completionRate(records, LocalDate.of(2026, 8, 16), schedule)
        assertEquals(2f / 3f, rate, 0.001f)
    }

    @Test
    fun `a bonus completion on an unscheduled day cannot push the rate above 100 percent`() {
        val records = listOf(record(17, true), record(18, true), record(19, true), record(21, true))
        val rate = StreakCalculator.completionRate(records, LocalDate.of(2026, 8, 21), monWedFri)
        assertEquals(1f, rate, 0.001f)
    }
}
