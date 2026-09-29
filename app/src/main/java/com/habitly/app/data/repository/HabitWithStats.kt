package com.habitly.app.data.repository

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.CompletionStatus

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
    /** Today's recorded status, or null if the day has not been judged yet. */
    val todayStatus: CompletionStatus? = null,
    /** Recorded status for each of the last 7 days, oldest first; null where nothing was logged. */
    val last7DaysStatus: List<CompletionStatus?> = emptyList(),
    /** The scheduled day that ended the streak, if it is broken — the day worth offering to rest. */
    val lastBrokenDueDay: java.time.LocalDate? = null,
    /** Units logged today, for habits carrying a numeric goal. */
    val todayProgress: Int = 0,
    /** The habit's own schedule, so callers can reason about which days it was ever due. */
    val schedule: com.habitly.app.domain.HabitSchedule = com.habitly.app.domain.HabitSchedule.DAILY,
    /** Contribution-grid shading (0..4) per day for the last 53 weeks; days with nothing logged are absent. */
    val levels: Map<java.time.LocalDate, Int> = emptyMap(),
    /** The note recorded against today's status, if any. */
    val todayNote: String? = null,
)
