package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitScheduleTest {

    @Test
    fun `a daily habit is due every day`() {
        val schedule = HabitSchedule(FrequencyType.DAILY)
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 22)))
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun `a specific-days habit is due only on those weekdays`() {
        val schedule = HabitSchedule(
            type = FrequencyType.SPECIFIC_DAYS,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 17)))  // Monday
        assertFalse(schedule.isDue(LocalDate.of(2026, 8, 18))) // Tuesday
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 19)))  // Wednesday
    }

    @Test
    fun `an interval habit is due every N days from its start date`() {
        val schedule = HabitSchedule(
            type = FrequencyType.INTERVAL,
            intervalDays = 3,
            startDate = LocalDate.of(2026, 8, 10),
        )
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 10)))
        assertFalse(schedule.isDue(LocalDate.of(2026, 8, 11)))
        assertFalse(schedule.isDue(LocalDate.of(2026, 8, 12)))
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 13)))
    }

    @Test
    fun `an interval habit is not due before it started`() {
        val schedule = HabitSchedule(
            type = FrequencyType.INTERVAL,
            intervalDays = 3,
            startDate = LocalDate.of(2026, 8, 10),
        )
        assertFalse(schedule.isDue(LocalDate.of(2026, 8, 7)))
    }

    @Test
    fun `a specific-days habit with no days set falls back to always due`() {
        // Guards legacy or malformed rows: never divide a completion rate by zero due days.
        val schedule = HabitSchedule(type = FrequencyType.SPECIFIC_DAYS, daysOfWeek = emptySet())
        assertTrue(schedule.isDue(LocalDate.of(2026, 8, 18)))
    }

    @Test
    fun `parse reads the stored comma-separated weekday names`() {
        val schedule = HabitSchedule.parse(
            type = FrequencyType.SPECIFIC_DAYS,
            frequencyDays = "MONDAY,WEDNESDAY,FRIDAY",
            intervalDays = null,
            startDate = LocalDate.of(2026, 8, 1),
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), schedule.daysOfWeek)
    }

    @Test
    fun `parse ignores unrecognised weekday names rather than throwing`() {
        val schedule = HabitSchedule.parse(
            type = FrequencyType.SPECIFIC_DAYS,
            frequencyDays = "MONDAY,NOTADAY",
            intervalDays = null,
            startDate = LocalDate.of(2026, 8, 1),
        )
        assertEquals(setOf(DayOfWeek.MONDAY), schedule.daysOfWeek)
    }

    @Test
    fun `dueDaysBetween counts only the days the habit was scheduled for`() {
        val schedule = HabitSchedule(
            type = FrequencyType.SPECIFIC_DAYS,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        // Mon 17 Aug 2026 through Sun 23 Aug 2026 holds exactly one Mon, Wed and Fri.
        val count = schedule.dueDaysBetween(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23))
        assertEquals(3, count)
    }
}
