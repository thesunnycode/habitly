package com.habitly.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemindersUiState(val habits: List<HabitEntity> = emptyList())

class RemindersViewModel(private val repository: HabitDataSource) : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Active habits in the user's order; an archived habit never reminds, so it isn't listed.
            repository.observeAllHabits().collect { all ->
                _uiState.value = RemindersUiState(
                    all.filter { !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.createdAt })),
                )
            }
        }
    }

    /** Sets the reminder as "HH:mm", or turns it off when [time] is null. */
    fun onSetReminder(id: Long, time: String?) {
        viewModelScope.launch { repository.setReminder(id, time) }
    }
}
