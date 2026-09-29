package com.habitly.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.ContributionLevels
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.StreakCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class HabitDetailViewModel(
    private val repository: HabitDataSource,
    private val habitId: Long,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState(today = today()))
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeHabitsWithStats(today()),
                repository.observeRecordsForHabit(habitId),
            ) { habits, records ->
                val match = habits.find { it.habit.id == habitId }
                val brokenDay = match?.lastBrokenDueDay
                // Simulate the repair rather than assume it helps: with two misses back to back,
                // fixing the later one restores nothing.
                val repairedStreak = if (brokenDay == null) {
                    0
                } else {
                    StreakCalculator.currentStreak(
                        records = records.filterNot { it.date == brokenDay } +
                            CompletionRecord(brokenDay, CompletionStatus.SKIPPED),
                        today = today(),
                        schedule = match.schedule,
                    )
                }
                val day = today()
                _uiState.value.copy(
                    habit = match?.habit,
                    records = records,
                    currentStreak = match?.currentStreak ?: 0,
                    bestStreak = match?.bestStreak ?: 0,
                    completionRate = match?.completionRate ?: 0f,
                    lastBrokenDueDay = brokenDay,
                    streakIfRepaired = repairedStreak,
                    totalDays = records.count { it.status == CompletionStatus.DONE },
                    todayStatus = records.find { it.date == day }?.status,
                    todayProgress = match?.todayProgress ?: 0,
                    levels = ContributionLevels.forHabit(records),
                    schedule = match?.schedule ?: HabitSchedule.DAILY,
                    today = day,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.value = _uiState.value.copy(month = month)
    }

    /**
     * Turns the day that ended the streak into a declared rest day, which restores the run.
     *
     * Seeing a streak sitting at zero with no way back is the moment people abandon a habit, so the
     * screen that reports the zero also offers the way out.
     */
    fun onRepairStreak() {
        val day = _uiState.value.lastBrokenDueDay ?: return
        viewModelScope.launch {
            repository.setStatus(habitId, day, CompletionStatus.SKIPPED)
        }
    }

    /**
     * Tapping a day in the month flips it between done and not done. Future days are never
     * editable. Goal habits are marked done outright (their unit count is logged from the sticky
     * button on today), and a tap on a done day clears it.
     */
    fun onToggleDay(date: LocalDate) {
        if (date.isAfter(today())) return
        val isGoal = _uiState.value.habit?.targetCount != null
        viewModelScope.launch {
            if (isGoal) {
                val current = _uiState.value.records.find { it.date == date }?.status
                repository.setStatus(habitId, date, if (current == CompletionStatus.DONE) null else CompletionStatus.DONE)
            } else {
                repository.toggleCompletion(habitId, date, null)
            }
        }
    }

    /** The long-press sheet's choice for [date]: done, rest day, or cleared (null). */
    fun onSetDayStatus(date: LocalDate, status: CompletionStatus?, note: String? = null) {
        if (date.isAfter(today())) return
        viewModelScope.launch { repository.setStatus(habitId, date, status, note) }
    }

    /** The sticky button: done / undo for plain habits, one more unit for goals in progress. */
    fun onToggleToday() {
        val state = _uiState.value
        val day = today()
        viewModelScope.launch {
            when {
                state.todayStatus == CompletionStatus.DONE -> repository.setStatus(habitId, day, null)
                state.habit?.targetCount != null -> repository.incrementProgress(habitId, day)
                else -> repository.toggleCompletion(habitId, day, null)
            }
        }
    }

    fun onArchive() {
        viewModelScope.launch {
            repository.setArchived(habitId, true)
            _uiState.value = _uiState.value.copy(isArchived = true)
        }
    }

    fun onDelete() {
        val habit = _uiState.value.habit ?: return
        viewModelScope.launch {
            repository.deleteHabit(habit)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
