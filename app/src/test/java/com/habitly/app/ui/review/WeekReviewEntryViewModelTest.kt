package com.habitly.app.ui.review

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private class EntryData(private val habits: List<HabitEntity>) : HabitDataSource {
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(habits)
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(emptyList())

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> = flowOf(null)
    override suspend fun addHabit(habit: HabitEntity): Long = 0L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> = emptySet()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override suspend fun reconcileAchievements(): Set<AchievementType> = emptySet()
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

class WeekReviewEntryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val monday9 = LocalDateTime.of(2026, 10, 5, 9, 0)
    private val dismissed = MutableStateFlow<String?>(null)

    private fun habitCreated(daysAgo: Long) = HabitEntity(
        name = "Run", icon = "run", colorHex = "#FF922B", category = "Fitness",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
        createdAt = monday9.toLocalDate().minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        isArchived = false,
    )

    private fun vm(daysAgo: Long) = WeekReviewEntryViewModel(
        data = EntryData(listOf(habitCreated(daysAgo))),
        weekStart = flowOf(DayOfWeek.MONDAY),
        dismissedWeek = dismissed,
        dismiss = { dismissed.value = it },
        now = { clock },
        ticks = ticks.map { },
    )

    private var clock = monday9
    private val ticks = MutableStateFlow(0)


    @Test fun bannerVisibleMondayMorningWithFiveTrackingDays() = runTest {
        val vm = vm(daysAgo = 4)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.bannerVisible)
        assertTrue(state.available)
        assertEquals("2026-09-28", state.weekStart)
    }

    @Test fun dismissingStoresTheReviewWeekAndHidesTheBanner() = runTest {
        val vm = vm(daysAgo = 4)
        advanceUntilIdle()
        vm.onDismissBanner()
        advanceUntilIdle()
        assertEquals("2026-09-28", dismissed.value)
        assertFalse(vm.uiState.value.bannerVisible)
        assertTrue(vm.uiState.value.available)
    }

    @Test fun bannerFollowsTheClockWithoutDataChanges() = runTest {
        clock = LocalDateTime.of(2026, 10, 4, 17, 0)
        val vm = vm(daysAgo = 4)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.bannerVisible)
        clock = LocalDateTime.of(2026, 10, 4, 18, 1)
        ticks.value++
        advanceUntilIdle()
        assertTrue(vm.uiState.value.bannerVisible)
        clock = LocalDateTime.of(2026, 10, 7, 9, 0)
        ticks.value++
        advanceUntilIdle()
        assertFalse(vm.uiState.value.bannerVisible)
    }

    @Test fun availableTracksTheMinimumHistory() = runTest {
        val short = vm(daysAgo = 1)
        advanceUntilIdle()
        assertFalse(short.uiState.value.available)
        assertFalse(short.uiState.value.bannerVisible)

        val exact = vm(daysAgo = 2)
        advanceUntilIdle()
        assertTrue(exact.uiState.value.available)
    }
}
