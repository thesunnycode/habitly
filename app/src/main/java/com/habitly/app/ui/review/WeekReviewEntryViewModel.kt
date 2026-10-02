package com.habitly.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

data class WeekReviewEntryState(
    /** Show the Today banner. */
    val bannerVisible: Boolean = false,
    /** The review can be opened at all (enough tracked days). */
    val available: Boolean = false,
    /** ISO date of the week the banner is about; what dismissing stores. */
    val weekStart: String? = null,
)

/** Decides whether the Today banner and the Insights link are on offer; the review itself is [WeekReviewViewModel]. */
class WeekReviewEntryViewModel(
    data: HabitDataSource,
    weekStart: Flow<DayOfWeek>,
    dismissedWeek: Flow<String?>,
    private val dismiss: suspend (String) -> Unit,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    val uiState: StateFlow<WeekReviewEntryState> = combine(
        data.observeAllHabits(), data.observeAllLogs(), weekStart, dismissedWeek,
    ) { habits, logs, ws, dismissed ->
        val current = now()
        val days = WeekReviewSchedule.trackingDays(habits, logs, current.toLocalDate())
        WeekReviewEntryState(
            bannerVisible = WeekReviewSchedule.bannerVisible(current, ws, dismissed, days),
            available = days >= WeekReviewSchedule.MIN_TRACKING_DAYS,
            weekStart = WeekReviewSchedule.reviewWeekStart(current, ws).toString(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeekReviewEntryState())

    fun onDismissBanner() {
        val week = uiState.value.weekStart ?: return
        viewModelScope.launch { dismiss(week) }
    }
}
