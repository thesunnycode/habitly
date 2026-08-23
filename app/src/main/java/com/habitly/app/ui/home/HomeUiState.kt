package com.habitly.app.ui.home

import com.habitly.app.data.repository.HabitWithStats

data class HomeUiState(
    val habits: List<HabitWithStats> = emptyList(),
    val completionPercent: Float = 0f,
    val isLoading: Boolean = true,
)
