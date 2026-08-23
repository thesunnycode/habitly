package com.habitly.app.ui.home

import com.habitly.app.data.repository.HabitWithStats
import java.time.LocalDate

data class HomeUiState(
    val habits: List<HabitWithStats> = emptyList(),
    val completionPercent: Float = 0f,
    val isLoading: Boolean = true,
    /** The day the list is showing. Defaults to today; the week strip can move it back. */
    val selectedDate: LocalDate = LocalDate.now(),
    /** The seven selectable days, oldest first, ending today. */
    val weekDays: List<LocalDate> = emptyList(),
) {
    val isViewingToday: Boolean get() = weekDays.isEmpty() || selectedDate == weekDays.last()
}
