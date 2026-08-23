package com.habitly.app.ui.allhabits

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

private fun habit(id: Long, name: String, category: String) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#000", category, FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

private class FakeHabitDataSource(habits: List<HabitWithStats>) : HabitDataSource {
    private val flow = MutableStateFlow(habits)
    override fun observeHabitsWithStats(today: LocalDate) = flow.asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class AllHabitsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val sampleHabits = listOf(
        habit(1, "Drink Water", "Health"),
        habit(2, "Read", "Learning"),
        habit(3, "Workout", "Health"),
    )

    @Test
    fun categoriesListsDistinctCategoriesFromAllHabits() = runTest {
        val viewModel = AllHabitsViewModel(FakeHabitDataSource(sampleHabits), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals(setOf("Health", "Learning"), viewModel.uiState.value.categories.toSet())
    }

    @Test
    fun searchQueryFiltersByNameCaseInsensitively() = runTest {
        val viewModel = AllHabitsViewModel(FakeHabitDataSource(sampleHabits), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        viewModel.onSearchQueryChange("water")
        assertEquals(listOf("Drink Water"), viewModel.uiState.value.filteredHabits.map { it.habit.name })
    }

    @Test
    fun categorySelectionFiltersToThatCategoryOnly() = runTest {
        val viewModel = AllHabitsViewModel(FakeHabitDataSource(sampleHabits), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        viewModel.onCategorySelect("Health")
        assertEquals(setOf("Drink Water", "Workout"), viewModel.uiState.value.filteredHabits.map { it.habit.name }.toSet())
    }

    @Test
    fun searchAndCategoryFiltersCombine() = runTest {
        val viewModel = AllHabitsViewModel(FakeHabitDataSource(sampleHabits), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        viewModel.onCategorySelect("Health")
        viewModel.onSearchQueryChange("work")
        assertEquals(listOf("Workout"), viewModel.uiState.value.filteredHabits.map { it.habit.name })
    }
}
