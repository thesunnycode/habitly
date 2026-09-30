package com.habitly.app.ui.habits

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitWithStats

enum class HabitsSegment { ACTIVE, ARCHIVED }

enum class HabitSort { CUSTOM, NAME, STREAK, CREATED }

/** One category's habits, in the current sort order. */
data class HabitGroup(val category: String, val habits: List<HabitWithStats>)

data class HabitsUiState(
    val segment: HabitsSegment = HabitsSegment.ACTIVE,
    val sort: HabitSort = HabitSort.CUSTOM,
    val query: String = "",
    val selectedCategory: String? = null,
    /** Category name to how many active habits it holds, for the filter chips. */
    val categories: List<Pair<String, Int>> = emptyList(),
    /** Active habits after filtering and sorting, grouped by category. */
    val groups: List<HabitGroup> = emptyList(),
    val archived: List<HabitEntity> = emptyList(),
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
) {
    val isFiltering get() = query.isNotBlank() || selectedCategory != null
}
