package com.habitly.app.ui.calendar

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private class FakeHabitDataSource(
    private val habits: List<HabitWithStats>,
    private val recordsByHabit: Map<Long, List<CompletionRecord>>,
) : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    val toggled = mutableListOf<Pair<Long, LocalDate>>()
    override fun observeHabitsWithStats(today: LocalDate) = MutableStateFlow(habits).asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        toggled += habitId to date
        return emptySet()
    }
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> {
        lastIncremented = habitId to date
        return emptySet()
    }

    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: com.habitly.app.domain.CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        return emptySet()
    }
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = MutableStateFlow(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<com.habitly.app.data.repository.AchievementProgress>> = MutableStateFlow(emptyList())
    override suspend fun reconcileAchievements() = emptySet<com.habitly.app.domain.AchievementType>()
    override suspend fun clearAchievements() {}
    override fun observeAllHabits(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeAllLogs(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitLogEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeRecordsForHabit(habitId: Long) = MutableStateFlow(recordsByHabit[habitId] ?: emptyList()).asStateFlow()
}

private fun habit(id: Long) = HabitWithStats(
    habit = HabitEntity(id, "H$id", "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

/** Created in August 2026, so every September day counts. */
private val AUGUST_FIRST = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun daily(id: Long, name: String) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#51CF66", "General", FrequencyType.DAILY, null, null, null, AUGUST_FIRST, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

private fun mwf(id: Long, name: String) = HabitWithStats(
    habit = HabitEntity(
        id, name, "star", "#4DABF7", "General", FrequencyType.SPECIFIC_DAYS, "MONDAY,WEDNESDAY,FRIDAY",
        null, null, AUGUST_FIRST, false,
    ),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
    schedule = HabitSchedule(
        FrequencyType.SPECIFIC_DAYS,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    ),
)

private fun done(habitId: Long, date: String) = habitId to CompletionRecord(LocalDate.parse(date), true)

class CalendarViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fake: FakeHabitDataSource

    private fun calendarVm(
        habits: List<HabitWithStats>,
        logs: List<Pair<Long, CompletionRecord>>,
    ): CalendarViewModel {
        fake = FakeHabitDataSource(
            habits = habits,
            recordsByHabit = logs.groupBy({ it.first }, { it.second }),
        )
        return CalendarViewModel(
            fake,
            today = LocalDate.of(2026, 9, 29),
            month = YearMonth.of(2026, 9),
        )
    }

    @Test fun selectingADayListsTheHabitsDueThatDay() = runTest {
        // Daily "Water" and Mon/Wed/Fri "Gym"; 2026-09-24 is a Thursday.
        val vm = calendarVm(habits = listOf(daily(1, "Water"), mwf(2, "Gym")), logs = listOf(done(1, "2026-09-24")))
        vm.onDaySelected(LocalDate.of(2026, 9, 24)); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(listOf("Water"), s.dayHabits.map { it.habit.name })
        assertEquals(CompletionStatus.DONE, s.dayHabits.single().status)
    }

    @Test fun dayFractionIsDoneOverDue() = runTest {
        val vm = calendarVm(habits = listOf(daily(1, "A"), daily(2, "B")), logs = listOf(done(1, "2026-09-10")))
        advanceUntilIdle()
        assertEquals(0.5f, vm.uiState.value.dayFraction[LocalDate.of(2026, 9, 10)]!!, 0.001f)
    }

    @Test fun aRestDayIsNeutralRatherThanMissed() = runTest {
        val rested = 2L to CompletionRecord(LocalDate.of(2026, 9, 10), CompletionStatus.SKIPPED)
        val vm = calendarVm(habits = listOf(daily(1, "A"), daily(2, "B")), logs = listOf(done(1, "2026-09-10"), rested))
        advanceUntilIdle()
        assertEquals(1f, vm.uiState.value.dayFraction[LocalDate.of(2026, 9, 10)]!!, 0.001f)
    }

    @Test fun togglingFromTheDaySheetWritesThatDate() = runTest {
        val vm = calendarVm(habits = listOf(daily(1, "A")), logs = emptyList())
        vm.onToggleHabitOnDay(1, LocalDate.of(2026, 9, 20)); advanceUntilIdle()
        assertEquals(listOf(1L to LocalDate.of(2026, 9, 20)), fake.toggled)
    }

    @Test fun dismissingClearsTheSelection() = runTest {
        val vm = calendarVm(habits = listOf(daily(1, "A")), logs = emptyList())
        vm.onDaySelected(LocalDate.of(2026, 9, 20)); vm.onDayDismissed(); advanceUntilIdle()
        assertNull(vm.uiState.value.selectedDate)
    }

    @Test
    fun mergedRecordsMarksADayCompletedIfAnyHabitWasCompletedThatDay() = runTest {
        val day = LocalDate.of(2026, 8, 20)
        val fake = FakeHabitDataSource(
            habits = listOf(habit(1), habit(2)),
            recordsByHabit = mapOf(
                1L to listOf(CompletionRecord(day, false)),
                2L to listOf(CompletionRecord(day, true)),
            ),
        )
        val viewModel = CalendarViewModel(fake)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mergedRecords.first { it.date == day }.completed)
    }

    @Test
    fun onMonthChangeUpdatesTheDisplayedMonth() = runTest {
        val fake = FakeHabitDataSource(habits = emptyList(), recordsByHabit = emptyMap())
        val viewModel = CalendarViewModel(fake)
        advanceUntilIdle()
        val target = java.time.YearMonth.of(2026, 6)
        viewModel.onMonthChange(target)
        assertEquals(target, viewModel.uiState.value.month)
    }
}
