package com.habitly.app.ui.allhabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class AllHabitsViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllHabitsUiState())
    val uiState: StateFlow<AllHabitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today()).collect { habits ->
                _uiState.value = _uiState.value.copy(allHabits = habits)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onCategorySelect(category: String?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = if (_uiState.value.selectedCategory == category) null else category,
        )
    }

    fun onToggleHabit(habitId: Long) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, today(), null)
        }
    }

    /** Records an explicit status for today, or clears it when [status] is null. */
    fun onSetStatus(habitId: Long, status: CompletionStatus?) {
        viewModelScope.launch {
            repository.setStatus(habitId, today(), status)
        }
    }
}
