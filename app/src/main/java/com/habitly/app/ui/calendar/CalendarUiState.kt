package com.habitly.app.ui.calendar

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One habit due on the selected day, with what was recorded for it. */
data class DayHabit(val habit: HabitEntity, val status: CompletionStatus?, val progress: Int)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val mergedRecords: List<CompletionRecord> = emptyList(),
    /** How many habits were completed on each day, for shading the activity graph. */
    val completionsByDate: Map<LocalDate, Int> = emptyMap(),
    val habitCount: Int = 0,
    /** The day whose sheet is open, or null when none is. */
    val selectedDate: LocalDate? = null,
    /** The habits due on [selectedDate], in the user's order. */
    val dayHabits: List<DayHabit> = emptyList(),
    /** Done ÷ due for each day of [month] up to today; absent when nothing was due. */
    val dayFraction: Map<LocalDate, Float> = emptyMap(),
    /** Done and due counts behind [dayFraction], for the day descriptions. */
    val dayCounts: Map<LocalDate, Pair<Int, Int>> = emptyMap(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)
