package com.habitly.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.prefs.TodayPrefs
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.ContributionLevels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** After this hour an unfinished habit carrying a streak is flagged as at risk. */
private val RISK_FROM: LocalTime = LocalTime.of(18, 0)

/** A streak shorter than this is not worth a warning. */
private const val RISK_MIN_STREAK = 2

private val FINISHED = setOf(CompletionStatus.DONE, CompletionStatus.SKIPPED)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: HabitDataSource,
    private val prefs: TodayPrefs,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> LocalTime = { LocalTime.now() },
) : ViewModel() {

    private val selectedDate = MutableStateFlow(today())

    private val _uiState = MutableStateFlow(
        TodayUiState(selectedDate = today(), weekDays = weekEndingToday()),
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Re-subscribing on every date change is what lets the list show a past day's state.
            val habitsForDay = selectedDate.flatMapLatest { date ->
                repository.observeHabitsWithStats(date).map { date to it }
            }
            combine(
                habitsForDay,
                prefs.userName,
                prefs.weekStart,
                prefs.lastCelebratedDate,
            ) { (date, habits), name, weekStart, celebrated ->
                buildState(date, habits, name, weekStart, celebrated)
            }.collect { _uiState.value = it }
        }
    }

    private fun buildState(
        date: LocalDate,
        habits: List<HabitWithStats>,
        name: String?,
        weekStart: java.time.DayOfWeek,
        celebrated: String?,
    ): TodayUiState {
        val weekDays = weekEndingToday()
        val (scheduled, notDue) = habits.partition { it.isDueToday }
        val (done, due) = scheduled.partition { it.todayStatus in FINISHED }
        val viewingToday = date == today()
        val atRisk = if (viewingToday && !now().isBefore(RISK_FROM)) {
            due.firstOrNull { it.currentStreak >= RISK_MIN_STREAK }
        } else {
            null
        }
        return TodayUiState(
            isLoading = false,
            selectedDate = date,
            weekDays = weekDays,
            dayCompletion = weekDays.associateWith { day -> completionOn(day, habits) },
            due = due,
            done = done,
            notDue = notDue,
            doneCount = done.size,
            dueCount = scheduled.size,
            userName = name,
            atRisk = atRisk,
            showCelebration = viewingToday && scheduled.isNotEmpty() && due.isEmpty() &&
                celebrated != today().toString(),
            weekStart = weekStart,
        )
    }

    /** Finished (done or rested) share of the habits scheduled on [day], read from their grid levels. */
    private fun completionOn(day: LocalDate, habits: List<HabitWithStats>): Float {
        val scheduled = habits.filter { it.schedule.isDue(day) }
        if (scheduled.isEmpty()) return 0f
        val finished = scheduled.count {
            val level = it.levels[day]
            level == ContributionLevels.MAX || level == SKIPPED_LEVEL
        }
        return finished.toFloat() / scheduled.size
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

    /** Ticks or un-ticks a plain habit on the selected day. */
    fun onToggleHabit(habitId: Long) {
        viewModelScope.launch { repository.toggleCompletion(habitId, selectedDate.value, null) }
    }

    /** Adds one unit towards a goal habit on the selected day; past the goal it clears the day. */
    fun onIncrement(habitId: Long) {
        viewModelScope.launch { repository.incrementProgress(habitId, selectedDate.value) }
    }

    /** Records an explicit status (and optional note) for the selected day, or clears it when [status] is null. */
    fun onSetStatus(habitId: Long, status: CompletionStatus?, note: String? = null) {
        viewModelScope.launch {
            repository.setStatus(habitId, selectedDate.value, status, note?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    /** Remembers that today's celebration has been seen so it shows at most once a day. */
    fun onCelebrationShown() {
        viewModelScope.launch { prefs.setLastCelebratedDate(today().toString()) }
    }

    private companion object {
        /** The level [ContributionLevels.forHabit] gives a declared rest day. */
        const val SKIPPED_LEVEL = 1
    }
}
