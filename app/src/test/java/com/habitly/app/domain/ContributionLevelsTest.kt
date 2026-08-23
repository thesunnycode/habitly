package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContributionLevelsTest {

    private fun day(d: Int) = LocalDate.of(2026, 8, d)

    @Test
    fun `a kept day shades at full strength`() {
        val levels = ContributionLevels.forHabit(listOf(CompletionRecord(day(1), CompletionStatus.DONE)))
        assertEquals(ContributionLevels.MAX, levels[day(1)])
    }

    @Test
    fun `a rest day is visible but faint`() {
        val levels = ContributionLevels.forHabit(listOf(CompletionRecord(day(2), CompletionStatus.SKIPPED)))
        assertEquals(1, levels[day(2)])
    }

    @Test
    fun `a missed day leaves the square empty`() {
        val levels = ContributionLevels.forHabit(listOf(CompletionRecord(day(3), CompletionStatus.MISSED)))
        assertEquals(null, levels[day(3)])
    }

    @Test
    fun `a full day across every habit shades at full strength`() {
        val levels = ContributionLevels.forAllHabits(mapOf(day(1) to 4), habitCount = 4)
        assertEquals(ContributionLevels.MAX, levels[day(1)])
    }

    @Test
    fun `partial days band proportionally rather than by raw count`() {
        val levels = ContributionLevels.forAllHabits(
            mapOf(day(1) to 1, day(2) to 2, day(3) to 3, day(4) to 4),
            habitCount = 4,
        )
        assertEquals(1, levels[day(1)])
        assertEquals(2, levels[day(2)])
        assertEquals(3, levels[day(3)])
        assertEquals(ContributionLevels.MAX, levels[day(4)])
    }

    @Test
    fun `a two habit day reads as strongly as an eight habit day when both are complete`() {
        val small = ContributionLevels.forAllHabits(mapOf(day(1) to 2), habitCount = 2)
        val large = ContributionLevels.forAllHabits(mapOf(day(1) to 8), habitCount = 8)
        assertEquals(small[day(1)], large[day(1)])
    }

    @Test
    fun `days with nothing done are absent rather than level zero`() {
        val levels = ContributionLevels.forAllHabits(mapOf(day(1) to 0), habitCount = 3)
        assertEquals(null, levels[day(1)])
    }

    @Test
    fun `no habits means no graph rather than a division by zero`() {
        assertEquals(emptyMap<LocalDate, Int>(), ContributionLevels.forAllHabits(mapOf(day(1) to 1), 0))
    }
}
