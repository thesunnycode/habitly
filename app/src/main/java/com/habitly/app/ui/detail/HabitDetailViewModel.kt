package com.habitly.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
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

    private val _uiState = MutableStateFlow(HabitDetailUiState())
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
                _uiState.value.copy(
                    habit = match?.habit,
                    records = records,
                    currentStreak = match?.currentStreak ?: 0,
                    bestStreak = match?.bestStreak ?: 0,
                    completionRate = match?.completionRate ?: 0f,
                    lastBrokenDueDay = brokenDay,
                    streakIfRepaired = repairedStreak,
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

    fun onArchive() {
        val habit = _uiState.value.habit ?: return
        viewModelScope.launch { repository.updateHabit(habit.copy(isArchived = true)) }
    }

    fun onDelete() {
        val habit = _uiState.value.habit ?: return
        viewModelScope.launch {
            repository.deleteHabit(habit)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
