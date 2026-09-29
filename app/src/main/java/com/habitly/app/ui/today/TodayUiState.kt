package com.habitly.app.ui.today

import com.habitly.app.data.repository.HabitWithStats
import java.time.DayOfWeek
import java.time.LocalDate

data class TodayUiState(
    val isLoading: Boolean = true,
    /** The day the list is showing. Defaults to today; the week strip can move it back. */
    val selectedDate: LocalDate = LocalDate.now(),
    /** The seven selectable days, oldest first, ending today. */
    val weekDays: List<LocalDate> = emptyList(),
    /** Share of each week day's due habits that were finished (done or rested), 0..1. */
    val dayCompletion: Map<LocalDate, Float> = emptyMap(),
    /** Due on the selected day and not yet finished. */
    val due: List<HabitWithStats> = emptyList(),
    /** Due on the selected day and done or rested. */
    val done: List<HabitWithStats> = emptyList(),
    /** Not scheduled on the selected day. */
    val notDue: List<HabitWithStats> = emptyList(),
    val doneCount: Int = 0,
    val dueCount: Int = 0,
    val userName: String? = null,
    /** The streak worth warning about this evening, if any. */
    val atRisk: HabitWithStats? = null,
    val showCelebration: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    val isViewingToday: Boolean get() = weekDays.isEmpty() || selectedDate == weekDays.last()
    val isEmpty: Boolean get() = !isLoading && due.isEmpty() && done.isEmpty() && notDue.isEmpty()
}
