package com.habitly.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
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
                _uiState.value.copy(
                    habit = match?.habit,
                    records = records,
                    currentStreak = match?.currentStreak ?: 0,
                    bestStreak = match?.bestStreak ?: 0,
                    completionRate = match?.completionRate ?: 0f,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.value = _uiState.value.copy(month = month)
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
