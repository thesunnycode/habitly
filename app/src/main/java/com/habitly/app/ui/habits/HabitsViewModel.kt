package com.habitly.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Filters and ordering chosen on screen; everything else in [HabitsUiState] derives from data. */
private data class Controls(
    val segment: HabitsSegment,
    val sort: HabitSort = HabitSort.CUSTOM,
    val query: String = "",
    val selectedCategory: String? = null,
)

class HabitsViewModel(
    private val repository: HabitDataSource,
    initialSegment: HabitsSegment,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val controls = MutableStateFlow(Controls(segment = initialSegment))

    val uiState: StateFlow<HabitsUiState> = combine(
        repository.observeHabitsWithStats(today()),
        repository.observeArchivedHabits(),
        controls,
    ) { active, archived, c ->
        HabitsUiState(
            segment = c.segment,
            sort = c.sort,
            query = c.query,
            selectedCategory = c.selectedCategory,
            categories = active.groupingBy { it.habit.category }.eachCount().toList()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first }),
            groups = group(active, c),
            archived = archived,
            activeCount = active.size,
            archivedCount = archived.size,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HabitsUiState(segment = initialSegment))

    fun onSegmentChange(segment: HabitsSegment) = controls.update { it.copy(segment = segment) }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    /** Selecting the already-selected category clears it, like tapping "All". */
    fun onCategorySelect(category: String?) = controls.update {
        it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
    }

    fun onSortChange(sort: HabitSort) = controls.update { it.copy(sort = sort) }

    fun onClearFilters() = controls.update { it.copy(query = "", selectedCategory = null) }

    fun onArchive(id: Long) {
        viewModelScope.launch { repository.setArchived(id, true) }
    }

    fun onRestore(id: Long) {
        viewModelScope.launch { repository.setArchived(id, false) }
    }

    fun onDeleteForever(id: Long) {
        viewModelScope.launch {
            val habit = uiState.value.archived.firstOrNull { it.id == id } ?: repository.getHabit(id) ?: return@launch
            repository.deleteHabit(habit)
        }
    }

    /**
     * Moves [fromId] into [toId]'s place within the full active list (not just the visible group),
     * so habits hidden by grouping keep their relative positions, then persists the whole order.
     */
    fun onMove(fromId: Long, toId: Long) {
        if (fromId == toId) return
        viewModelScope.launch {
            val ids = repository.observeHabitsWithStats(today()).first().map { it.habit.id }.toMutableList()
            val toIndex = ids.indexOf(toId)
            if (toIndex < 0 || !ids.remove(fromId)) return@launch
            ids.add(toIndex, fromId)
            repository.reorder(ids)
        }
    }

    private fun group(active: List<HabitWithStats>, c: Controls): List<HabitGroup> {
        val query = c.query.trim()
        val filtered = active
            .filter { c.selectedCategory == null || it.habit.category == c.selectedCategory }
            .filter {
                query.isEmpty() ||
                    it.habit.name.contains(query, ignoreCase = true) ||
                    it.habit.category.contains(query, ignoreCase = true)
            }
        val sorted = when (c.sort) {
            HabitSort.CUSTOM -> filtered
            HabitSort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.habit.name })
            HabitSort.STREAK -> filtered.sortedByDescending { it.currentStreak }
            HabitSort.CREATED -> filtered.sortedBy { it.habit.createdAt }
        }
        return sorted.groupBy { it.habit.category }
            .map { (category, habits) -> HabitGroup(category, habits) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category })
    }
}
