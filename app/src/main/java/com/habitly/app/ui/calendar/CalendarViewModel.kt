package com.habitly.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(private val repository: HabitDataSource) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(LocalDate.now())
                .flatMapLatest { habits ->
                    if (habits.isEmpty()) {
                        flowOf(emptyList<CompletionRecord>() to 0)
                    } else {
                        combine(habits.map { repository.observeRecordsForHabit(it.habit.id) }) { perHabit ->
                            perHabit.toList().flatten() to habits.size
                        }
                    }
                }
                .collect { (allRecords, habitCount) ->
                    val byDate = allRecords.groupBy { it.date }
                    _uiState.value = _uiState.value.copy(
                        mergedRecords = byDate.map { (date, records) ->
                            CompletionRecord(date, records.any { it.completed })
                        },
                        completionsByDate = byDate.mapValues { (_, records) ->
                            records.count { it.completed }
                        },
                        habitCount = habitCount,
                    )
                }
        }
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.value = _uiState.value.copy(month = month)
    }
}
