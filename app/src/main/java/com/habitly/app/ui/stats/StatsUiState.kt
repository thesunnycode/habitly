package com.habitly.app.ui.stats

data class StatsUiState(
    val weeklyCompletionByDay: List<Float> = List(7) { 0f },
    val bestHabitName: String? = null,
    val worstHabitName: String? = null,
    val overallCompletionRate: Float = 0f,
)
