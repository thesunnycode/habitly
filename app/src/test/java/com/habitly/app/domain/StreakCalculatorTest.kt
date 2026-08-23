package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
