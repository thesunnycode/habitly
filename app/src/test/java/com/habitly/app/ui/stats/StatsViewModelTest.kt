package com.habitly.app.ui.stats

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 8, 24) // a Monday

private fun habit(
    id: Long,
    name: String,
    currentStreak: Int = 0,
    bestStreak: Int = 0,
) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#6B9080", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    completionRate = 0f,
    isCompletedToday = false,
)

private fun done(vararg dates: LocalDate) = dates.map { CompletionRecord(it, CompletionStatus.DONE) }

private fun daysBack(from: LocalDate, count: Int) =
    (0 until count).map { from.minusDays(it.toLong()) }

private class FakeHabitDataSource(
    habits: List<HabitWithStats>,
    private val recordsByHabit: Map<Long, List<CompletionRecord>> = emptyMap(),
) : HabitDataSource {
    private val flow = MutableStateFlow(habits)
    override fun observeHabitsWithStats(today: LocalDate) = flow.asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate) = emptySet<AchievementType>()
    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String?,
    ) = emptySet<AchievementType>()
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> =
        MutableStateFlow(recordsByHabit[habitId].orEmpty())
}

class StatsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        habits: List<HabitWithStats>,
        records: Map<Long, List<CompletionRecord>> = emptyMap(),
    ) = StatsViewModel(
        FakeHabitDataSource(habits, records),
        today = { TODAY },
        computeDispatcher = mainDispatcherRule.dispatcher,
    )

    @Test
    fun withNoHabitsTheScreenReportsNoHistoryRatherThanZeroPercent() = runTest {
        val vm = viewModel(emptyList())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasAnyHistory)
        assertEquals(0, vm.uiState.value.daysOfHistory)
    }

    @Test
    fun completionRateCoversTheSelectedPeriodOnly() = runTest {
        // Kept every day for the last 7; nothing before that.
        val records = mapOf(1L to done(*daysBack(TODAY, 7).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()
        assertEquals(1f, vm.uiState.value.completionRate, 0.001f)
    }

    @Test
    fun theDeltaStaysHiddenUntilTwoFullPeriodsOfHistoryExist() = runTest {
        // Only 10 days of history, so a week-on-week comparison would be half-invented.
        val records = mapOf(1L to done(*daysBack(TODAY, 10).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showDelta)
        assertNull(vm.uiState.value.previousRate)
    }

    @Test
    fun theDeltaAppearsOnceThereAreTwoPeriodsToCompare() = runTest {
        // 21 days of history: kept the recent week, missed most of the one before.
        val recent = daysBack(TODAY, 7)
        val older = listOf(TODAY.minusDays(20))
        val records = mapOf(1L to done(*(recent + older).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showDelta)
        assertTrue("delta should be positive, was ${state.delta}", (state.delta ?: 0f) > 0f)
    }

    @Test
    fun changingThePeriodRecalculatesTheRate() = runTest {
        // Perfect for the last 7 days, nothing in the 23 before that.
        val records = mapOf(1L to done(*daysBack(TODAY, 7).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()
        val weekRate = vm.uiState.value.completionRate

        vm.onPeriodChange(StatsPeriod.MONTH)
        val monthRate = vm.uiState.value.completionRate

        assertEquals(StatsPeriod.MONTH, vm.uiState.value.period)
        assertTrue("month rate $monthRate should be below week rate $weekRate", monthRate <= weekRate)
    }

    @Test
    fun thePillRowEndsOnTodayRatherThanOnTheCalendarWeek() = runTest {
        // The hero percentage measures the trailing seven days. If the pills were pinned to Monday
        // the two would describe different windows on the same card, which is the fault this
        // screen was rebuilt to remove.
        val records = mapOf(1L to done(*daysBack(TODAY, 3).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(7, state.weekStatuses.size)
        assertEquals(7, state.weekLabels.size)
        assertEquals(6, state.todayIndex)
        assertEquals(CompletionStatus.DONE, state.weekStatuses[6])
    }

    @Test
    fun theOldestPillIsSixDaysBackSoNoSlotIsInTheFuture() = runTest {
        // Kept only today, so the six older slots are unrecorded and none can be a future day.
        val records = mapOf(1L to done(TODAY))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CompletionStatus.DONE, state.weekStatuses[6])
        assertTrue(state.weekStatuses.take(6).all { it == null })
        assertEquals(
            TODAY.minusDays(6).dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            ).take(1),
            state.weekLabels.first(),
        )
    }

    @Test
    fun aDayWhereOnlySomeHabitsWereKeptReadsAsPartial() = runTest {
        val records = mapOf(
            1L to done(TODAY),
            2L to emptyList<CompletionRecord>(),
        )
        val vm = viewModel(listOf(habit(1, "Water"), habit(2, "Gym")), records)
        advanceUntilIdle()
        assertEquals(CompletionStatus.PARTIAL, vm.uiState.value.weekStatuses[6])
    }

    @Test
    fun recordsReportTheLongestStreakAcrossHabitsAndFlagAPersonalBest() = runTest {
        val records = mapOf(1L to done(*daysBack(TODAY, 5).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water", currentStreak = 5, bestStreak = 5)), records)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(5, state.currentStreak)
        assertEquals(5, state.bestStreak)
        assertTrue(state.isPersonalBest)
        assertEquals(5, state.totalCompletions)
    }

    @Test
    fun aHabitWithLessThanAWeekOfHistoryIsNotJudged() = runTest {
        val records = mapOf(1L to done(*daysBack(TODAY, 3).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()

        val verdict = vm.uiState.value.habitVerdicts.single()
        assertEquals(VerdictGroup.NOT_ENOUGH_DATA, verdict.group)
    }

    @Test
    fun aHabitWhoseRateFellSharplyIsGroupedAsSlipping() = runTest {
        // Perfect for the older week, one day in the recent one.
        val older = daysBack(TODAY.minusDays(7), 7)
        val recent = listOf(TODAY.minusDays(6))
        val records = mapOf(1L to done(*(older + recent).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Reading")), records)
        advanceUntilIdle()

        val verdict = vm.uiState.value.habitVerdicts.single()
        assertEquals(VerdictGroup.SLIPPING, verdict.group)
        assertTrue("delta should be negative, was ${verdict.delta}", (verdict.delta ?: 0f) < 0f)
    }

    @Test
    fun aWellKeptHabitIsGroupedAsGoingWell() = runTest {
        val records = mapOf(1L to done(*daysBack(TODAY, 14).toTypedArray()))
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()
        assertEquals(VerdictGroup.GOING_WELL, vm.uiState.value.habitVerdicts.single().group)
    }

    @Test
    fun verdictsAreOrderedByGroupThenByRate() = runTest {
        val records = mapOf(
            1L to done(*daysBack(TODAY, 14).toTypedArray()),
            2L to done(*daysBack(TODAY, 2).toTypedArray()),
        )
        val vm = viewModel(listOf(habit(2, "New"), habit(1, "Established")), records)
        advanceUntilIdle()

        val groups = vm.uiState.value.habitVerdicts.map { it.group }
        assertEquals(listOf(VerdictGroup.GOING_WELL, VerdictGroup.NOT_ENOUGH_DATA), groups)
    }

    @Test
    fun restDaysDoNotCountAgainstTheCompletionRate() = runTest {
        val records = mapOf(
            1L to done(*daysBack(TODAY, 6).toTypedArray()) +
                CompletionRecord(TODAY.minusDays(6), CompletionStatus.SKIPPED),
        )
        val vm = viewModel(listOf(habit(1, "Water")), records)
        advanceUntilIdle()
        assertEquals(1f, vm.uiState.value.completionRate, 0.001f)
    }
}
