package com.habitly.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.ContributionLevels
import com.habitly.app.domain.HabitHistory
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.InsightEngine
import com.habitly.app.domain.WeekdayBreakdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val SLIPPING_DROP = 0.15f
private const val GOING_WELL_RATE = 0.6f

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repository: HabitDataSource,
    private val today: () -> LocalDate = { LocalDate.now() },
    /**
     * Where the aggregation runs. Off the main thread in production, and injectable so tests keep
     * control of scheduling rather than racing a real background pool.
     */
    private val computeDispatcher: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    /** What the user has picked. Kept apart from the state so every build reads one consistent pair. */
    private data class Selection(val period: StatsPeriod, val habitId: Long?)

    private val selection = MutableStateFlow(Selection(StatsPeriod.WEEK, null))

    init {
        viewModelScope.launch {
            repository.observeHabitsWithStats(today())
                .flatMapLatest { habits ->
                    if (habits.isEmpty()) {
                        flowOf(emptyList<Pair<HabitWithStats, List<CompletionRecord>>>())
                    } else {
                        combine(habits.map { repository.observeRecordsForHabit(it.habit.id) }) { perHabit ->
                            habits.zip(perHabit.toList())
                        }
                    }
                }
                .combine(selection) { pairs, picked -> pairs to picked }
                // This screen aggregates every record of every habit over months. That is far too
                // much work for the main thread — doing it there froze the app into an ANR the
                // first time the screen was opened with real seeded history.
                // mapLatest drops a build superseded by a newer selection or data change, so quick
                // chip taps can never settle on a stale one that happened to finish last.
                .mapLatest { (pairs, picked) -> build(pairs, picked.period, picked.habitId) }
                .flowOn(computeDispatcher)
                .collect { _uiState.value = it }
        }
    }

    fun onPeriodChange(period: StatsPeriod) {
        // Optimistic: flip the selector immediately, then recompute off the main thread.
        _uiState.value = _uiState.value.copy(period = period)
        selection.value = selection.value.copy(period = period)
    }

    /** Filters the figures to one habit, or back to all habits with null. */
    fun onHabitFilterChange(id: Long?) {
        _uiState.value = _uiState.value.copy(selectedHabitId = id)
        selection.value = selection.value.copy(habitId = id)
    }

    private fun build(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        period: StatsPeriod,
        habitId: Long?,
    ): StatsUiState {
        val now = today()
        if (pairs.isEmpty()) return StatsUiState(period = period, today = now, isLoading = false)

        // A filter to a habit that is no longer active (archived, deleted) falls back to all.
        val selected = habitId?.let { id -> pairs.firstOrNull { it.first.habit.id == id } }
        // The habits the headline figures describe: the selected one, or every habit.
        val scope = selected?.let { listOf(it) } ?: pairs
        val scopeRecords = scope.flatMap { it.second }
        val window = barDays(period, now)

        val allRecords = pairs.flatMap { it.second }
        val earliest = allRecords.minOfOrNull { it.date }
        val daysOfHistory = earliest?.let { (ChronoUnit.DAYS.between(it, now) + 1).toInt() } ?: 0

        val periodStart = now.minusDays((period.days - 1).toLong())
        val previousStart = periodStart.minusDays(period.days.toLong())

        val rate = rateOver(scope, periodStart, now)
        val previousRate = when {
            selected != null -> habitPreviousRate(selected, period, now)
            daysOfHistory >= period.days * 2 -> rateOver(scope, previousStart, periodStart.minusDays(1))
            else -> null
        }

        // The trailing seven days, ending today — deliberately NOT the calendar week. The hero
        // percentage above the pills measures the last seven days, and pinning the pills to
        // Monday made the two disagree on the same card, which is the exact fault this screen was
        // rebuilt to remove. A day counts as done only when every habit due that day was kept.
        val weekStart = now.minusDays(6)
        val statusIndex = pairs.map { (stats, records) ->
            stats to records.associate { it.date to it.status }
        }
        val weekStatuses = (0 until 7).map { offset ->
            mergedStatusFor(pairs, weekStart.plusDays(offset.toLong()), now, statusIndex)
        }
        val weekLabels = (0 until 7).map {
            weekStart.plusDays(it.toLong()).dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
        }

        val byWeekday = WeekdayBreakdown.completionsByWeekday(allRecords)
        val currentStreak = scope.maxOfOrNull { it.first.currentStreak } ?: 0
        val bestStreak = scope.maxOfOrNull { it.first.bestStreak } ?: 0

        val insights = InsightEngine.insights(
            histories = scope.map { (stats, records) ->
                HabitHistory(
                    name = stats.habit.name,
                    records = records,
                    currentStreak = stats.currentStreak,
                    bestStreak = stats.bestStreak,
                    schedule = stats.schedule,
                )
            },
            today = now,
        )

        return StatsUiState(
            period = period,
            selectedHabitId = selected?.first?.habit?.id,
            chips = pairs.map { (stats, _) ->
                HabitChip(stats.habit.id, stats.habit.name, stats.habit.colorHex)
            },
            window = window,
            dayStacks = if (selected == null) dayStacks(pairs, window) else emptyMap(),
            habitLevels = selected?.let { ContributionLevels.forHabit(it.second) } ?: emptyMap(),
            insights = insights,
            completionRate = rate,
            previousRate = previousRate,
            daysOfHistory = daysOfHistory,
            weekStatuses = weekStatuses,
            weekLabels = weekLabels,
            todayIndex = 6,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalCompletions = scopeRecords.count { it.status == CompletionStatus.DONE },
            perfectDays = perfectDays(pairs, now),
            restDays = scopeRecords.count { it.status == CompletionStatus.SKIPPED },
            isPersonalBest = currentStreak > 0 && currentStreak >= bestStreak,
            completionsByWeekday = byWeekday,
            weakestWeekday = WeekdayBreakdown.weakestWeekday(byWeekday),
            habitVerdicts = verdicts(pairs, period, now),
            yearLevels = ContributionLevels.forAllHabits(
                completionsByDate = allRecords.filter { it.status == CompletionStatus.DONE }
                    .groupingBy { it.date }.eachCount(),
                habitCount = pairs.size,
            ),
            periodBars = window.map { rateOver(pairs, it, it) },
            periodBarLabels = window.map { day ->
                if (period == StatsPeriod.WEEK) {
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
                } else {
                    "${day.dayOfMonth}"
                }
            },
            today = now,
            isLoading = false,
        )
    }

    /** The days the hero bars cover: each day of the window, oldest first. Year shows a grid instead. */
    private fun barDays(period: StatsPeriod, now: LocalDate): List<LocalDate> =
        if (period == StatsPeriod.YEAR) emptyList()
        else (period.days - 1 downTo 0).map { now.minusDays(it.toLong()) }

    /**
     * Per day of the window, one slice per habit kept (or rested) that day, in repository order so
     * the colours sit in the same order as the chips. Days with nothing get no entry.
     */
    private fun dayStacks(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        window: List<LocalDate>,
    ): Map<LocalDate, List<DaySlice>> {
        val index = pairs.map { (stats, records) ->
            stats.habit.colorHex to records.associate { it.date to it.status }
        }
        return window.mapNotNull { day ->
            val slices = index.mapNotNull { (colorHex, byDate) ->
                when (byDate[day]) {
                    CompletionStatus.DONE -> DaySlice(colorHex, rested = false)
                    CompletionStatus.SKIPPED -> DaySlice(colorHex, rested = true)
                    else -> null
                }
            }
            if (slices.isEmpty()) null else day to slices
        }.toMap()
    }

    /** Share of due days across all habits that were kept, over a window. */
    private fun rateOver(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        from: LocalDate,
        to: LocalDate,
    ): Float = rateOrNull(pairs, from, to) ?: 0f

    /**
     * One habit's rate over the period before the current one, or null when that period is not
     * fully inside the habit's own history or had no due days. Without this a habit only days old
     * would compare against an empty week and show a large, invented gain.
     */
    private fun habitPreviousRate(
        pair: Pair<HabitWithStats, List<CompletionRecord>>,
        period: StatsPeriod,
        now: LocalDate,
    ): Float? {
        val habitDays = pair.second.minOfOrNull { it.date }
            ?.let { (ChronoUnit.DAYS.between(it, now) + 1).toInt() } ?: 0
        if (habitDays < period.days * 2) return null
        val periodStart = now.minusDays((period.days - 1).toLong())
        return rateOrNull(listOf(pair), periodStart.minusDays(period.days.toLong()), periodStart.minusDays(1))
    }

    /** As [rateOver], but null when the window had no due days at all. */
    private fun rateOrNull(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        from: LocalDate,
        to: LocalDate,
    ): Float? {
        var due = 0
        var done = 0
        pairs.forEach { (stats, records) ->
            val start = maxOf(from, records.minOfOrNull { it.date } ?: from)
            due += stats.schedule.dueDaysBetween(start, to)
            // Rest days are neutral, so they come out of the denominator as well as the numerator.
            due -= records.count {
                it.status == CompletionStatus.SKIPPED && !it.date.isBefore(start) && !it.date.isAfter(to)
            }
            done += records.count {
                it.status == CompletionStatus.DONE && !it.date.isBefore(from) && !it.date.isAfter(to)
            }
        }
        if (due <= 0) return null
        return (done.toFloat() / due).coerceIn(0f, 1f)
    }

    private fun mergedStatusFor(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        date: LocalDate,
        now: LocalDate,
        index: List<Pair<HabitWithStats, Map<LocalDate, CompletionStatus>>> = pairs.map { (s, r) ->
            s to r.associate { it.date to it.status }
        },
    ): CompletionStatus? {
        if (date.isAfter(now)) return null
        val dueHabits = index.filter { it.first.schedule.isDue(date) }
        if (dueHabits.isEmpty()) return null

        val statuses = dueHabits.map { (_, byDate) -> byDate[date] }
        return when {
            statuses.all { it == CompletionStatus.DONE } -> CompletionStatus.DONE
            statuses.all { it == CompletionStatus.SKIPPED } -> CompletionStatus.SKIPPED
            statuses.any { it == CompletionStatus.DONE } -> CompletionStatus.PARTIAL
            else -> null
        }
    }

    private fun perfectDays(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        now: LocalDate,
    ): Int {
        val earliest = pairs.flatMap { it.second }.minOfOrNull { it.date } ?: return 0
        // Built once and reused for every day, rather than scanning each habit's record list per
        // day, which made this quadratic in the length of the history.
        val index = pairs.map { (stats, records) ->
            stats to records.associate { it.date to it.status }
        }
        var count = 0
        var day = earliest
        while (!day.isAfter(now)) {
            if (mergedStatusFor(pairs, day, now, index) == CompletionStatus.DONE) count++
            day = day.plusDays(1)
        }
        return count
    }

    /**
     * Sorts habits into going well, slipping, or not judged yet. Borrowed from Apple Fitness's
     * Trends tab, where the grouping carries the message and "not enough data" is a named state
     * rather than a blank row. The list is ordered by rate, highest first; ties fall back to group.
     */
    private fun verdicts(
        pairs: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        period: StatsPeriod,
        now: LocalDate,
    ): List<HabitVerdict> {
        val periodStart = now.minusDays((period.days - 1).toLong())

        return pairs.map { pair ->
            val (stats, records) = pair
            val single = listOf(pair)
            val rate = rateOver(single, periodStart, now)
            val previous = habitPreviousRate(pair, period, now)
            val habitDays = records.minOfOrNull { it.date }
                ?.let { (ChronoUnit.DAYS.between(it, now) + 1).toInt() } ?: 0

            val group = when {
                habitDays < 7 -> VerdictGroup.NOT_ENOUGH_DATA
                previous != null && previous - rate >= SLIPPING_DROP -> VerdictGroup.SLIPPING
                rate >= GOING_WELL_RATE -> VerdictGroup.GOING_WELL
                previous == null -> VerdictGroup.GOING_WELL
                else -> VerdictGroup.SLIPPING
            }

            HabitVerdict(
                id = stats.habit.id,
                name = stats.habit.name,
                colorHex = stats.habit.colorHex,
                rate = rate,
                delta = previous?.let { rate - it },
                group = group,
                spark = sparkFor(single, now),
            )
        }.sortedWith(compareByDescending<HabitVerdict> { it.rate }.thenBy { it.group.ordinal })
    }

    /** Six weekly rates, oldest first — enough to show a direction, not enough to invite reading. */
    private fun sparkFor(
        single: List<Pair<HabitWithStats, List<CompletionRecord>>>,
        now: LocalDate,
    ): List<Float> = (5 downTo 0).map { weeksBack ->
        val end = now.minusWeeks(weeksBack.toLong())
        rateOver(single, end.minusDays(6), end)
    }
}
