package com.habitly.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.reminder.ReminderNotifications
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: HabitDataSource,
    private val today: LocalDate = LocalDate.now(),
    month: YearMonth = YearMonth.from(today),
    weekStart: Flow<DayOfWeek> = flowOf(DayOfWeek.MONDAY),
    private val notifications: ReminderNotifications = ReminderNotifications.None,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(month = month, today = today))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    /** The latest habits with their records, kept so selection changes rebuild without waiting. */
    private var latest: List<Pair<HabitWithStats, List<CompletionRecord>>> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today)
                .flatMapLatest { habits ->
                    if (habits.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(habits.map { repository.observeRecordsForHabit(it.habit.id) }) { perHabit ->
                            habits.zip(perHabit.toList())
                        }
                    }
                }
                .collect { pairs ->
                    latest = pairs
                    publish()
                }
        }
        viewModelScope.launch {
            weekStart.collect { start ->
                _uiState.value = _uiState.value.copy(weekStart = start)
            }
        }
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.value = _uiState.value.copy(month = month)
        publish()
    }

    /** Opens the day sheet. Future days hold nothing to record, so they are not selectable. */
    fun onDaySelected(date: LocalDate) {
        if (date.isAfter(today)) return
        _uiState.value = _uiState.value.copy(selectedDate = date)
        publish()
    }

    fun onDayDismissed() {
        _uiState.value = _uiState.value.copy(selectedDate = null, dayHabits = emptyList())
    }

    fun onToggleHabitOnDay(habitId: Long, date: LocalDate) {
        if (date.isAfter(today)) return
        val habit = latest.firstOrNull { it.first.habit.id == habitId }?.first?.habit
        viewModelScope.launch {
            if (habit?.targetCount != null) {
                repository.incrementProgress(habitId, date)
            } else {
                repository.toggleCompletion(habitId, date, null)
            }
            // Logging today's entry makes its reminder and streak nudge stale.
            if (date == today) notifications.cancelFor(habitId)
        }
    }

    /** Rebuilds everything derived from [latest], so the sheet always reflects the observed data. */
    private fun publish() {
        val current = _uiState.value
        val pairs = latest
        val byDate = pairs.flatMap { it.second }.groupBy { it.date }
        val statusIndex = pairs.map { (stats, records) -> stats to records.associate { it.date to it.status } }
        val starts = pairs.associate { (stats, records) -> stats.habit.id to startOf(stats, records) }
        fun isDue(stats: HabitWithStats, date: LocalDate): Boolean =
            !date.isBefore(starts.getValue(stats.habit.id)) && stats.schedule.isDue(date)

        val counts = mutableMapOf<LocalDate, Pair<Int, Int>>()
        for (day in 1..current.month.lengthOfMonth()) {
            val date = current.month.atDay(day)
            if (date.isAfter(today)) break
            // A rest day is neutral: it leaves the day's denominator, as it does in Stats.
            val due = statusIndex.filter { (stats, statuses) ->
                isDue(stats, date) && statuses[date] != CompletionStatus.SKIPPED
            }
            if (due.isEmpty()) continue
            val done = due.count { (_, statuses) -> statuses[date] == CompletionStatus.DONE }
            counts[date] = done to due.size
        }

        val selected = current.selectedDate
        val dayHabits = if (selected == null) {
            emptyList()
        } else {
            statusIndex.filter { (stats, _) -> isDue(stats, selected) }.map { (stats, statuses) ->
                val status = statuses[selected]
                DayHabit(
                    habit = stats.habit,
                    status = status,
                    progress = progressFor(stats, selected, status),
                )
            }
        }

        _uiState.value = current.copy(
            mergedRecords = byDate.map { (date, records) -> CompletionRecord(date, records.any { it.completed }) },
            completionsByDate = byDate.mapValues { (_, records) -> records.count { it.completed } },
            habitCount = pairs.size,
            dayHabits = dayHabits,
            dayCounts = counts,
            dayFraction = counts.mapValues { (_, c) -> c.first.toFloat() / c.second },
        )
    }

    /**
     * The first day a habit counts as due: when it was created, or its earliest record if history
     * predates that (seeded and imported habits carry older logs than their creation time).
     */
    private fun startOf(stats: HabitWithStats, records: List<CompletionRecord>): LocalDate {
        val created = Instant.ofEpochMilli(stats.habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val earliest = records.minOfOrNull { it.date } ?: return created
        return minOf(created, earliest)
    }

    /**
     * Units logged on [date]. Only today's count is observable; a past goal day reads as the full
     * target when it was kept and nothing otherwise.
     */
    private fun progressFor(stats: HabitWithStats, date: LocalDate, status: CompletionStatus?): Int = when {
        date == today -> stats.todayProgress
        status == CompletionStatus.DONE -> stats.habit.targetCount ?: 0
        else -> 0
    }
}
