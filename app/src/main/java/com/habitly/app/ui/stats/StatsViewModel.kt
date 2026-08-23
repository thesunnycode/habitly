package com.habitly.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.WeekdayBreakdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today())
                .flatMapLatest { habits ->
                    if (habits.isEmpty()) {
                        flowOf(habits to emptyList())
                    } else {
                        combine(habits.map { repository.observeRecordsForHabit(it.habit.id) }) { perHabit ->
                            habits to perHabit.toList().flatten()
                        }
                    }
                }
                .collect { (habits, allRecords) ->
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
                val byWeekday = WeekdayBreakdown.completionsByWeekday(allRecords)
                _uiState.value = StatsUiState(
                    weeklyCompletionByDay = dailyRates,
                    bestHabitName = best,
                    worstHabitName = worst,
                    overallCompletionRate = overall,
                    completionsByWeekday = byWeekday,
                    weakestWeekday = WeekdayBreakdown.weakestWeekday(byWeekday),
                    totalCompletions = byWeekday.values.sum(),
                )
            }
        }
    }
}
