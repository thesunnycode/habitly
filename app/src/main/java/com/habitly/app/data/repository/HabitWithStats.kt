package com.habitly.app.data.repository

import com.habitly.app.data.local.HabitEntity

data class HabitWithStats(
    val habit: HabitEntity,
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRate: Float,
    val isCompletedToday: Boolean,
    /** Completion flag for each of the last 7 days, oldest first, ending today. */
    val last7Days: List<Boolean> = emptyList(),
    /** Whether the habit's own frequency actually asks for it today. */
    val isDueToday: Boolean = true,
    /** Whether each of the last 7 days was a scheduled day, oldest first, ending today. */
    val last7DaysDue: List<Boolean> = emptyList(),
)
