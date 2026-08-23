package com.habitly.app.ui.stats

data class StatsUiState(
    /** Fraction of habits completed on each of the last 7 days, oldest first, ending today. */
    val weeklyCompletionByDay: List<Float> = List(7) { 0f },
    val bestHabitName: String? = null,
    val worstHabitName: String? = null,
    val overallCompletionRate: Float = 0f,
)
