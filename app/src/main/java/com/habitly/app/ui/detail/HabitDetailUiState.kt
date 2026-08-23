package com.habitly.app.ui.detail

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.CompletionRecord
import java.time.YearMonth

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    val records: List<CompletionRecord> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completionRate: Float = 0f,
    val month: YearMonth = YearMonth.now(),
    val isDeleted: Boolean = false,
)
