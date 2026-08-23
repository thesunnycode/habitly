package com.habitly.app.ui.stats

data class StatsUiState(
    /** Fraction of habits completed on each of the last 7 days, oldest first, ending today. */
    val weeklyCompletionByDay: List<Float> = List(7) { 0f },
    val bestHabitName: String? = null,
    val worstHabitName: String? = null,
    val overallCompletionRate: Float = 0f,
    /** How many completions landed on each weekday, across all habits and all history. */
    val completionsByWeekday: Map<java.time.DayOfWeek, Int> = emptyMap(),
    /** The weekday kept least often — the one worth doing something about. */
    val weakestWeekday: java.time.DayOfWeek? = null,
    val totalCompletions: Int = 0,
)
