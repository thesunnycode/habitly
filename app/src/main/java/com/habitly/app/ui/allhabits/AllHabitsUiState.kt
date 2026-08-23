package com.habitly.app.ui.allhabits

import com.habitly.app.data.repository.HabitWithStats

data class AllHabitsUiState(
    val allHabits: List<HabitWithStats> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
) {
    val categories: List<String>
        get() = allHabits.map { it.habit.category }.distinct()

    val filteredHabits: List<HabitWithStats>
        get() = allHabits
            .filter { selectedCategory == null || it.habit.category == selectedCategory }
            .filter { searchQuery.isBlank() || it.habit.name.contains(searchQuery, ignoreCase = true) }
}
