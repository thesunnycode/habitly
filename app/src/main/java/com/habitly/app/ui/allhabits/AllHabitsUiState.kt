package com.habitly.app.ui.allhabits

import com.habitly.app.data.repository.HabitWithStats

data class AllHabitsUiState(
    val allHabits: List<HabitWithStats> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
) {
    val categories: List<String>
        get() = allHabits.map { it.habit.category }.distinct()

    /** How many habits sit under each category, for the filter chip badges. */
    val categoryCounts: Map<String, Int>
        get() = allHabits.groupingBy { it.habit.category }.eachCount()

    val filteredHabits: List<HabitWithStats>
        get() = allHabits
            .filter { selectedCategory == null || it.habit.category == selectedCategory }
            .filter { searchQuery.isBlank() || it.habit.name.contains(searchQuery, ignoreCase = true) }

    /** Still to do today, within the current filters — the section users act on. */
    val todoHabits: List<HabitWithStats>
        get() = filteredHabits.filterNot { it.isCompletedToday }

    /** Already ticked off today, shown below the to-do list as visible progress. */
    val completedHabits: List<HabitWithStats>
        get() = filteredHabits.filter { it.isCompletedToday }

    /** Today's progress counts deliberately ignore the filters — it's a whole-day figure. */
    val doneToday: Int get() = allHabits.count { it.isCompletedToday }

    val totalToday: Int get() = allHabits.size

    val progress: Float
        get() = if (totalToday == 0) 0f else doneToday.toFloat() / totalToday
}
