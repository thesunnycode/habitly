package com.habitly.app.ui.detail

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class HabitDetailUiState(
    val habit: HabitEntity? = null,
    val records: List<CompletionRecord> = emptyList(),
    /** The note recorded against each day, where there is one. */
    val notes: Map<LocalDate, String> = emptyMap(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completionRate: Float = 0f,
    val month: YearMonth = YearMonth.now(),
    val isDeleted: Boolean = false,
    /** The scheduled day that ended the current streak, if it is broken. */
    val lastBrokenDueDay: LocalDate? = null,
    /**
     * The streak that would be restored by turning [lastBrokenDueDay] into a rest day. Zero when
     * repairing that one day would not actually reconnect a run — in which case offering the repair
     * would be promising something it cannot deliver.
     */
    val streakIfRepaired: Int = 0,
    /** Every day ever marked done. */
    val totalDays: Int = 0,
    /** Today's recorded status, or null when nothing is logged yet. */
    val todayStatus: CompletionStatus? = null,
    /** Units logged today, for habits carrying a numeric goal. */
    val todayProgress: Int = 0,
    /** Contribution-grid shading (0..4) per day, from this screen's own records. */
    val levels: Map<LocalDate, Int> = emptyMap(),
    /** Set once this screen has archived the habit, so it closes and the previous one offers Undo. */
    val justArchived: Boolean = false,
    /** The habit's schedule, for the meta line. */
    val schedule: HabitSchedule = HabitSchedule.DAILY,
    /** The day the screen treats as today; later days cannot be edited. */
    val today: LocalDate = LocalDate.now(),
    /** The first column of the year grid and the month, from Settings. */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** An archived habit is shown read-mostly: no sticky check-in, and Restore instead of Archive. */
    val isHabitArchived: Boolean get() = habit?.isArchived == true
}
