package com.habitly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val selectedDate = MutableStateFlow(today())

    private val _uiState = MutableStateFlow(
        HomeUiState(selectedDate = today(), weekDays = weekEndingToday()),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Re-subscribing on every date change is what lets the list show a past day's state.
            selectedDate.flatMapLatest { date -> repository.observeHabitsWithStats(date) }
                .collect { habits ->
                    val percent = if (habits.isEmpty()) {
                        0f
                    } else {
                        habits.count { it.isCompletedToday }.toFloat() / habits.size
                    }
                    _uiState.value = _uiState.value.copy(
                        habits = habits,
                        completionPercent = percent,
                        isLoading = false,
                        selectedDate = selectedDate.value,
                        weekDays = weekEndingToday(),
                    )
                }
        }
    }

    private fun weekEndingToday(): List<LocalDate> {
        val end = today()
        return (6 downTo 0).map { offset -> end.minusDays(offset.toLong()) }
    }

    /** Moves the list to another day so a forgotten day can still be logged. Future days are ignored. */
    fun onSelectDate(date: LocalDate) {
        if (date.isAfter(today())) return
        selectedDate.value = date
        _uiState.value = _uiState.value.copy(selectedDate = date, weekDays = weekEndingToday())
    }

    fun onToggleHabit(habitId: Long) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, selectedDate.value, null)
        }
    }

    /** Records an explicit status for the selected day, or clears it when [status] is null. */
    fun onSetStatus(habitId: Long, status: CompletionStatus?) {
        viewModelScope.launch {
            repository.setStatus(habitId, selectedDate.value, status)
        }
    }
}
