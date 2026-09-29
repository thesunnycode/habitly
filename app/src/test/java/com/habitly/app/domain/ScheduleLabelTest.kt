package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

class ScheduleLabelTest {

    @Test
    fun `a daily habit reads Daily`() {
        assertEquals("Daily", HabitSchedule.DAILY.label(Locale.ENGLISH))
    }

    @Test
    fun `specific days are listed in week order`() {
        val schedule = HabitSchedule(
            FrequencyType.SPECIFIC_DAYS,
            daysOfWeek = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        assertEquals("Mon, Wed, Fri", schedule.label(Locale.ENGLISH))
    }

    @Test
    fun `an interval reads Every N days`() {
        assertEquals("Every 2 days", HabitSchedule(FrequencyType.INTERVAL, intervalDays = 2).label(Locale.ENGLISH))
        assertEquals("Daily", HabitSchedule(FrequencyType.INTERVAL, intervalDays = 1).label(Locale.ENGLISH))
    }
}
