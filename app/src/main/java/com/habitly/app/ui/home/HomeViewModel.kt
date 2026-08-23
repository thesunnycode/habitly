package com.habitly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today()).collect { habits ->
                val percent = if (habits.isEmpty()) {
                    0f
                } else {
                    habits.count { it.isCompletedToday }.toFloat() / habits.size
                }
                _uiState.value = HomeUiState(habits = habits, completionPercent = percent, isLoading = false)
            }
        }
    }

    fun onToggleHabit(habitId: Long) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, today(), null)
        }
    }
}
