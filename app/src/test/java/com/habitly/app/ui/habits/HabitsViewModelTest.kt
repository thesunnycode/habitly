package com.habitly.app.ui.habits

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private fun h(id: Long, name: String, category: String, streak: Int = 0, createdAt: Long = id) = HabitWithStats(
    habit = HabitEntity(
        id, name, "star", "#000", category, FrequencyType.DAILY, null, null, null, createdAt, false,
        sortOrder = id.toInt(),
    ),
    currentStreak = streak, bestStreak = streak, completionRate = 0f, isCompletedToday = false,
)

/** In-memory source: habits flip between the active and archived lists as they are archived or restored. */
private class FakeHabitsSource(
    active: List<HabitWithStats> = emptyList(),
    archived: List<HabitEntity> = emptyList(),
) : HabitDataSource {
    private val all = MutableStateFlow(active + archived.map { HabitWithStats(it, 0, 0, 0f, false) })
    var lastReorder: List<Long>? = null
    var lastDeleted: HabitEntity? = null

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> =
        all.map { list -> list.filterNot { it.habit.isArchived } }
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> =
        all.map { list -> list.map { it.habit }.filter { it.isArchived } }
    override suspend fun setArchived(habitId: Long, archived: Boolean) {
        all.value = all.value.map { if (it.habit.id == habitId) it.copy(habit = it.habit.copy(isArchived = archived)) else it }
    }
    override suspend fun reorder(orderedIds: List<Long>) { lastReorder = orderedIds }
    override suspend fun deleteHabit(habit: HabitEntity) {
        lastDeleted = habit
        all.value = all.value.filterNot { it.habit.id == habit.id }
    }
    override suspend fun getHabit(habitId: Long): HabitEntity? = all.value.firstOrNull { it.habit.id == habitId }?.habit
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String?,
    ): Set<AchievementType> = emptySet()
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<com.habitly.app.data.repository.AchievementProgress>> = MutableStateFlow(emptyList())
    override suspend fun reconcileAchievements() = emptySet<com.habitly.app.domain.AchievementType>()
    override suspend fun clearAchievements() {}
    override fun observeAllHabits(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeAllLogs(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitLogEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class HabitsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun vmWith(active: List<HabitWithStats>) = HabitsViewModel(FakeHabitsSource(active = active), HabitsSegment.ACTIVE)

    @Test fun groupsActiveHabitsByCategory() = runTest {
        val vm = vmWith(active = listOf(h(1, "Water", "Health"), h(2, "Read", "Mind"), h(3, "Sleep", "Health")))
        advanceUntilIdle()
        assertEquals(listOf("Health", "Mind"), vm.uiState.value.groups.map { it.category })
        assertEquals(listOf("Water", "Sleep"), vm.uiState.value.groups[0].habits.map { it.habit.name })
        assertEquals(listOf("Health" to 2, "Mind" to 1), vm.uiState.value.categories)
    }

    @Test fun searchMatchesNameOrCategory() = runTest {
        val vm = vmWith(active = listOf(h(1, "Water", "Health"), h(2, "Read", "Mind")))
        vm.onQueryChange("mind"); advanceUntilIdle()
        assertEquals(listOf("Read"), vm.uiState.value.groups.flatMap { it.habits }.map { it.habit.name })
    }

    @Test fun searchIsCaseInsensitiveOnTheName() = runTest {
        val vm = vmWith(active = listOf(h(1, "Drink Water", "Health"), h(2, "Read", "Learning")))
        vm.onQueryChange("WATER"); advanceUntilIdle()
        assertEquals(listOf("Drink Water"), vm.uiState.value.groups.flatMap { it.habits }.map { it.habit.name })
    }

    @Test fun categorySelectionFiltersToThatCategoryOnly() = runTest {
        val vm = vmWith(active = listOf(h(1, "Drink Water", "Health"), h(2, "Read", "Learning"), h(3, "Workout", "Health")))
        vm.onCategorySelect("Health"); advanceUntilIdle()
        assertEquals(listOf("Health"), vm.uiState.value.groups.map { it.category })
        assertEquals(listOf("Drink Water", "Workout"), vm.uiState.value.groups.single().habits.map { it.habit.name })
        assertTrue(vm.uiState.value.isFiltering)
        // Counts on the chips stay whole-list figures.
        assertEquals(listOf("Health" to 2, "Learning" to 1), vm.uiState.value.categories)
    }

    @Test fun searchAndCategoryFiltersCombine() = runTest {
        val vm = vmWith(active = listOf(h(1, "Drink Water", "Health"), h(2, "Read", "Learning"), h(3, "Workout", "Health")))
        vm.onCategorySelect("Health")
        vm.onQueryChange("work"); advanceUntilIdle()
        assertEquals(listOf("Workout"), vm.uiState.value.groups.flatMap { it.habits }.map { it.habit.name })
    }

    @Test fun clearFiltersResetsQueryAndCategory() = runTest {
        val vm = vmWith(active = listOf(h(1, "Water", "Health"), h(2, "Read", "Mind")))
        vm.onCategorySelect("Health")
        vm.onQueryChange("zzz"); advanceUntilIdle()
        assertTrue(vm.uiState.value.groups.isEmpty())
        vm.onClearFilters(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isFiltering)
        assertEquals(2, vm.uiState.value.groups.flatMap { it.habits }.size)
    }

    @Test fun sortByStreakOrdersWithinGroups() = runTest {
        val vm = vmWith(active = listOf(h(1, "A", "X", streak = 1), h(2, "B", "X", streak = 9)))
        vm.onSortChange(HabitSort.STREAK); advanceUntilIdle()
        assertEquals(listOf("B", "A"), vm.uiState.value.groups.single().habits.map { it.habit.name })
    }

    @Test fun sortByNameIsCaseInsensitive() = runTest {
        val vm = vmWith(active = listOf(h(1, "banana", "X"), h(2, "Apple", "X"), h(3, "cherry", "X")))
        vm.onSortChange(HabitSort.NAME); advanceUntilIdle()
        assertEquals(listOf("Apple", "banana", "cherry"), vm.uiState.value.groups.single().habits.map { it.habit.name })
    }

    @Test fun sortByCreatedPutsNewestFirst() = runTest {
        val vm = vmWith(active = listOf(h(1, "A", "X", createdAt = 30), h(2, "B", "X", createdAt = 10), h(3, "C", "X", createdAt = 20)))
        vm.onSortChange(HabitSort.CREATED); advanceUntilIdle()
        assertEquals(listOf("A", "C", "B"), vm.uiState.value.groups.single().habits.map { it.habit.name })
    }

    @Test fun archiveAndRestoreMoveBetweenSegments() = runTest {
        val fake = FakeHabitsSource(active = listOf(h(1, "Read", "Mind")))
        val vm = HabitsViewModel(fake, HabitsSegment.ACTIVE)
        vm.onArchive(1); advanceUntilIdle()
        assertEquals(0, vm.uiState.value.activeCount); assertEquals(1, vm.uiState.value.archivedCount)
        vm.onRestore(1); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeCount)
    }

    @Test fun deleteForeverRemovesTheArchivedHabit() = runTest {
        val archived = h(5, "Read 20 Minutes", "Mind").habit.copy(isArchived = true)
        val fake = FakeHabitsSource(archived = listOf(archived))
        val vm = HabitsViewModel(fake, HabitsSegment.ARCHIVED)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.archivedCount)
        vm.onDeleteForever(5); advanceUntilIdle()
        assertEquals(5L, fake.lastDeleted?.id)
        assertEquals(0, vm.uiState.value.archivedCount)
    }

    @Test fun movingAHabitReordersTheFullList() = runTest {
        val fake = FakeHabitsSource(active = listOf(h(1, "A", "X"), h(2, "B", "X"), h(3, "C", "X")))
        val vm = HabitsViewModel(fake, HabitsSegment.ACTIVE)
        vm.onMove(fromId = 3, toId = 1); advanceUntilIdle()
        assertEquals(listOf(3L, 1L, 2L), fake.lastReorder)
    }

    @Test fun movingDownPlacesTheHabitAfterTheTarget() = runTest {
        val fake = FakeHabitsSource(active = listOf(h(1, "A", "X"), h(2, "B", "Y"), h(3, "C", "X")))
        val vm = HabitsViewModel(fake, HabitsSegment.ACTIVE)
        vm.onMove(fromId = 1, toId = 3); advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 1L), fake.lastReorder)
    }

    @Test fun startsOnTheRequestedSegment() = runTest {
        val vm = HabitsViewModel(FakeHabitsSource(), HabitsSegment.ARCHIVED); advanceUntilIdle()
        assertEquals(HabitsSegment.ARCHIVED, vm.uiState.value.segment)
    }

    @Test fun segmentChangeSwitchesTheSegment() = runTest {
        val vm = HabitsViewModel(FakeHabitsSource(), HabitsSegment.ACTIVE)
        vm.onSegmentChange(HabitsSegment.ARCHIVED); advanceUntilIdle()
        assertEquals(HabitsSegment.ARCHIVED, vm.uiState.value.segment)
        assertFalse(vm.uiState.value.isLoading)
    }
}
