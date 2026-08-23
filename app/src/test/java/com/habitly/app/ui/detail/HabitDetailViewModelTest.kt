package com.habitly.app.ui.detail

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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private fun habitWithStats(id: Long, streak: Int) = HabitWithStats(
    habit = HabitEntity(id, "Read", "book", "#000", "Learning", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = streak, bestStreak = streak, completionRate = 0.8f, isCompletedToday = true,
)

private class FakeHabitDataSource(private val habits: List<HabitWithStats>) : HabitDataSource {
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    var archivedId: Long? = null
    var deletedHabit: HabitEntity? = null

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> =
        MutableStateFlow(habits).asStateFlow()
    override suspend fun addHabit(habit: HabitEntity): Long = 1L
    override suspend fun updateHabit(habit: HabitEntity) { if (habit.isArchived) archivedId = habit.id }
    override suspend fun deleteHabit(habit: HabitEntity) { deletedHabit = habit }
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: com.habitly.app.domain.CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        return emptySet()
    }
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class HabitDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateExposesTheMatchingHabitAndItsStreaks() = runTest {
        val fake = FakeHabitDataSource(listOf(habitWithStats(5L, streak = 3), habitWithStats(6L, streak = 0)))
        val viewModel = HabitDetailViewModel(fake, habitId = 5L, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals("Read", viewModel.uiState.value.habit?.name)
        assertEquals(3, viewModel.uiState.value.currentStreak)
    }

    @Test
    fun onArchiveUpdatesTheHabitAsArchived() = runTest {
        val fake = FakeHabitDataSource(listOf(habitWithStats(5L, streak = 3)))
        val viewModel = HabitDetailViewModel(fake, habitId = 5L, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        viewModel.onArchive()
        advanceUntilIdle()
        assertEquals(5L, fake.archivedId)
    }

    @Test
    fun onDeleteRemovesTheHabit() = runTest {
        val fake = FakeHabitDataSource(listOf(habitWithStats(5L, streak = 3)))
        val viewModel = HabitDetailViewModel(fake, habitId = 5L, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        viewModel.onDelete()
        advanceUntilIdle()
        assertEquals(5L, fake.deletedHabit?.id)
    }

    @Test
    fun onMonthChangeUpdatesTheDisplayedMonth() = runTest {
        val fake = FakeHabitDataSource(listOf(habitWithStats(5L, streak = 3)))
        val viewModel = HabitDetailViewModel(fake, habitId = 5L, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        val target = java.time.YearMonth.of(2026, 7)
        viewModel.onMonthChange(target)
        assertEquals(target, viewModel.uiState.value.month)
    }
}
