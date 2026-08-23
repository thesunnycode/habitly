package com.habitly.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today()).collect { habits ->
                val overall = if (habits.isEmpty()) 0f else habits.map { it.completionRate }.average().toFloat()
                val best = habits.maxByOrNull { it.completionRate }?.habit?.name
                val worst = habits.minByOrNull { it.completionRate }?.habit?.name
                _uiState.value = StatsUiState(
                    weeklyCompletionByDay = List(7) { overall },
                    bestHabitName = best,
                    worstHabitName = worst,
                    overallCompletionRate = overall,
                )
            }
        }
    }
}
