package com.habitly.app.domain

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The short human label for a schedule, as shown in card meta lines: "Daily", "Mon, Wed, Fri",
 * "Every 2 days". Schedules that are due every day in practice read as "Daily".
 */
fun HabitSchedule.label(locale: Locale = Locale.getDefault()): String = when (type) {
    FrequencyType.DAILY -> "Daily"
    FrequencyType.SPECIFIC_DAYS ->
        if (daysOfWeek.isEmpty() || daysOfWeek.size == DayOfWeek.entries.size) {
            "Daily"
        } else {
            daysOfWeek.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }
    FrequencyType.INTERVAL -> {
        val step = intervalDays
        if (step == null || step <= 1) "Daily" else "Every $step days"
    }
}
