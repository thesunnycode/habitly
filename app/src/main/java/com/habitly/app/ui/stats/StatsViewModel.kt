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
                // Real per-day rate across all habits, not a repeated overall average: each
                // habit's own last7Days (already tracked for its Home-card mini heatmap) tells
                // us whether it was completed on that specific day.
                val dailyRates = if (habits.isEmpty()) {
                    List(7) { 0f }
                } else {
                    (0 until 7).map { dayIndex ->
                        habits.count { it.last7Days.getOrElse(dayIndex) { false } }.toFloat() / habits.size
                    }
                }
                _uiState.value = StatsUiState(
                    weeklyCompletionByDay = dailyRates,
                    bestHabitName = best,
                    worstHabitName = worst,
                    overallCompletionRate = overall,
                )
            }
        }
    }
}
