package com.habitly.app.ui.stats

import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.Insight
import java.time.DayOfWeek
import java.time.LocalDate

enum class StatsPeriod(val label: String, val days: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
}

/** Which bucket a habit falls into. The grouping is the point: a flat list has no opinion. */
enum class VerdictGroup { GOING_WELL, SLIPPING, NOT_ENOUGH_DATA }

data class HabitVerdict(
    val id: Long,
    val name: String,
    val colorHex: String,
    val rate: Float,
    /** Change against the previous period of the same length, or null when there isn't one. */
    val delta: Float?,
    val group: VerdictGroup,
    /** A few points of recent history, purely for the row's sparkline. */
    val spark: List<Float> = emptyList(),
)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val insights: List<Insight> = emptyList(),
    /** Completion across the selected period. */
    val completionRate: Float = 0f,
    /** The same figure for the previous period, or null when history is too short to compare. */
    val previousRate: Float? = null,
    /** How many days of history exist at all, which is what gates each card. */
    val daysOfHistory: Int = 0,
    /** Status for each day of the current week, Monday first. */
    val weekStatuses: List<CompletionStatus?> = emptyList(),
    val weekLabels: List<String> = emptyList(),
    val todayIndex: Int = 6,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val perfectDays: Int = 0,
    /** True when the live streak is the longest there has ever been. */
    val isPersonalBest: Boolean = false,
    val completionsByWeekday: Map<DayOfWeek, Int> = emptyMap(),
    val weakestWeekday: DayOfWeek? = null,
    val habitVerdicts: List<HabitVerdict> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
) {
    val delta: Float? get() = previousRate?.let { completionRate - it }

    /** Deltas need a previous period to be honest about, so they stay hidden until there is one. */
    val showDelta: Boolean get() = delta != null && daysOfHistory >= period.days * 2

    val hasAnyHistory: Boolean get() = daysOfHistory > 0
}
