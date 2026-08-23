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
    val habits = MutableStateFlow<List<HabitWithStats>>(emptyList())
    var lastToggledId: Long? = null

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = habits.asStateFlow()
    override suspend fun addHabit(habit: HabitEntity): Long = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        lastToggledId = habitId
        return emptySet()
    }
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
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
}
