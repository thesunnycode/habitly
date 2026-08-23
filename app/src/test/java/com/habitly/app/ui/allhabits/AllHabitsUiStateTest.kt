package com.habitly.app.ui.allhabits

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Test

private fun habit(
    id: Long,
    name: String,
    category: String,
    done: Boolean = false,
    streak: Int = 0,
) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#000", category, FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = streak,
    bestStreak = streak,
    completionRate = 0f,
    isCompletedToday = done,
)

class AllHabitsUiStateTest {

    private val state = AllHabitsUiState(
        allHabits = listOf(
            habit(1, "Drink Water", "Health", done = true),
            habit(2, "Read", "Learning"),
            habit(3, "Workout", "Health", done = true),
            habit(4, "Meditate", "Wellness"),
        ),
    )

    @Test
    fun doneTodayCountsOnlyHabitsCompletedToday() {
        assertEquals(2, state.doneToday)
    }

    @Test
    fun totalTodayCountsEveryHabitRegardlessOfFilters() {
        assertEquals(4, state.copy(selectedCategory = "Health").totalToday)
    }

    @Test
    fun progressIsTheFractionOfHabitsDoneToday() {
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun progressIsZeroWhenThereAreNoHabits() {
        assertEquals(0f, AllHabitsUiState().progress, 0.001f)
    }

    @Test
    fun todoHabitsHoldsTheFilteredHabitsThatAreNotDoneYet() {
        assertEquals(listOf("Read", "Meditate"), state.todoHabits.map { it.habit.name })
    }

    @Test
    fun completedHabitsHoldsTheFilteredHabitsAlreadyDone() {
        assertEquals(listOf("Drink Water", "Workout"), state.completedHabits.map { it.habit.name })
    }

    @Test
    fun sectionsRespectTheActiveCategoryFilter() {
        val filtered = state.copy(selectedCategory = "Health")
        assertEquals(emptyList<String>(), filtered.todoHabits.map { it.habit.name })
        assertEquals(listOf("Drink Water", "Workout"), filtered.completedHabits.map { it.habit.name })
    }

    @Test
    fun categoryCountsReportsHowManyHabitsEachCategoryHolds() {
        assertEquals(mapOf("Health" to 2, "Learning" to 1, "Wellness" to 1), state.categoryCounts)
    }
}
