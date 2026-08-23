package com.habitly.app.ui.calendar

import com.habitly.app.domain.CompletionRecord
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val mergedRecords: List<CompletionRecord> = emptyList(),
)
