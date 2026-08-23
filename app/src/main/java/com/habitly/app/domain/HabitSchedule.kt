package com.habitly.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * When a habit is actually expected. Every habit already stores a frequency, but until now nothing
 * read it back — streaks and completion rates treated every calendar day as a day you owed the
 * habit, so a three-times-a-week habit kept perfectly reported about 43%.
 */
data class HabitSchedule(
    val type: FrequencyType,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int? = null,
    val startDate: LocalDate? = null,
) {
    fun isDue(date: LocalDate): Boolean = when (type) {
        FrequencyType.DAILY -> true
        // An empty day set is a malformed row rather than a habit due on no days at all; treating it
        // as always-due keeps rates finite instead of dividing by zero due days.
        FrequencyType.SPECIFIC_DAYS -> daysOfWeek.isEmpty() || date.dayOfWeek in daysOfWeek
        FrequencyType.INTERVAL -> {
            val anchor = startDate
            val step = intervalDays
            when {
                anchor == null || step == null || step <= 0 -> true
                date.isBefore(anchor) -> false
                else -> ChronoUnit.DAYS.between(anchor, date) % step == 0L
            }
        }
    }

    /** How many days in [from]..[to] inclusive this habit was expected on. */
    fun dueDaysBetween(from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0
        var count = 0
        var day = from
        while (!day.isAfter(to)) {
            if (isDue(day)) count++
            day = day.plusDays(1)
        }
        return count
    }

    companion object {
        val DAILY = HabitSchedule(FrequencyType.DAILY)

        /** Builds a schedule from the columns as they are stored on the habit row. */
        fun parse(
            type: FrequencyType,
            frequencyDays: String?,
            intervalDays: Int?,
            startDate: LocalDate,
        ): HabitSchedule = HabitSchedule(
            type = type,
            daysOfWeek = frequencyDays
                ?.split(',')
                ?.mapNotNull { name -> runCatching { DayOfWeek.valueOf(name.trim()) }.getOrNull() }
                ?.toSet()
                .orEmpty(),
            intervalDays = intervalDays,
            startDate = startDate,
        )
    }
}
