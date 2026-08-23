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
    /** The scheduled day that ended the current streak, if it is broken. */
    val lastBrokenDueDay: java.time.LocalDate? = null,
    /**
     * The streak that would be restored by turning [lastBrokenDueDay] into a rest day. Zero when
     * repairing that one day would not actually reconnect a run — in which case offering the repair
     * would be promising something it cannot deliver.
     */
    val streakIfRepaired: Int = 0,
)
