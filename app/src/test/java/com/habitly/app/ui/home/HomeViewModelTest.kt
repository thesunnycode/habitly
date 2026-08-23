package com.habitly.app.ui.home

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
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

private class FakeHabitDataSource : HabitDataSource {
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    val habits = MutableStateFlow<List<HabitWithStats>>(emptyList())
    var lastToggledId: Long? = null
    var lastToggledDate: LocalDate? = null
    var lastQueriedDate: LocalDate? = null

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> {
        lastQueriedDate = today
        return habits.asStateFlow()
    }
    override suspend fun addHabit(habit: HabitEntity): Long = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        lastToggledId = habitId
        lastToggledDate = date
        return emptySet()
    }
    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: com.habitly.app.domain.CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        lastToggledDate = date
        return emptySet()
    }
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long) = MutableStateFlow(emptyList<com.habitly.app.domain.CompletionRecord>())
}

private fun habit(id: Long, completed: Boolean) = HabitWithStats(
    habit = HabitEntity(id, "H$id", "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = completed,
)

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateReflectsHabitsAndComputesCompletionPercent() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        fake.habits.value = listOf(habit(1, true), habit(2, false))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.habits.size)
        assertEquals(0.5f, state.completionPercent, 0.001f)
    }

    @Test
    fun onToggleHabitDelegatesToRepositoryWithTheGivenId() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        viewModel.onToggleHabit(42L)
        advanceUntilIdle()
        assertEquals(42L, fake.lastToggledId)
    }

    @Test
    fun completionPercentIsZeroWhenThereAreNoHabits() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals(0f, viewModel.uiState.value.completionPercent, 0.001f)
    }

    @Test
    fun theSelectedDayStartsAtTodayAndTheWeekEndsThere() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = HomeViewModel(fake, today = { today })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(today, state.selectedDate)
        assertEquals(7, state.weekDays.size)
        assertEquals(today, state.weekDays.last())
        assertEquals(LocalDate.of(2026, 8, 16), state.weekDays.first())
    }

    @Test
    fun selectingAnEarlierDayRequeriesTheRepositoryForThatDay() = runTest {
        // Logging a day you forgot is the whole point: the list has to show that day's state.
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 20))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 20), viewModel.uiState.value.selectedDate)
        assertEquals(LocalDate.of(2026, 8, 20), fake.lastQueriedDate)
    }

    @Test
    fun togglingWhileViewingAnEarlierDayWritesToThatDay() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 19))
        viewModel.onToggleHabit(7L)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 19), fake.lastToggledDate)
    }

    @Test
    fun settingAStatusWhileViewingAnEarlierDayWritesToThatDay() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = HomeViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 18))
        viewModel.onSetStatus(3L, com.habitly.app.domain.CompletionStatus.SKIPPED)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 18), fake.lastToggledDate)
    }

    @Test
    fun aFutureDayCannotBeSelected() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = HomeViewModel(fake, today = { today })
        advanceUntilIdle()

        viewModel.onSelectDate(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun isViewingTodayIsFalseOnlyWhenAnEarlierDayIsSelected() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = HomeViewModel(fake, today = { today })
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isViewingToday)

        viewModel.onSelectDate(today.minusDays(2))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isViewingToday)
    }
}
