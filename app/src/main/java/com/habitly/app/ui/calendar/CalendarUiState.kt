package com.habitly.app.ui.calendar

import com.habitly.app.domain.CompletionRecord
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val mergedRecords: List<CompletionRecord> = emptyList(),
    /** How many habits were completed on each day, for shading the activity graph. */
    val completionsByDate: Map<java.time.LocalDate, Int> = emptyMap(),
    val habitCount: Int = 0,
)
