package com.habitly.app.ui.reminders

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private fun habit(id: Long, sortOrder: Int, archived: Boolean = false, reminder: String? = null) = HabitEntity(
    id, "H$id", "star", "#FF922B", "Mind", FrequencyType.DAILY, null, null, reminder, 0L, archived, sortOrder = sortOrder,
)

private class FakeRemindersDataSource(habits: List<HabitEntity>) : HabitDataSource {
    val habits = MutableStateFlow(habits)
    override fun observeAllHabits(): Flow<List<HabitEntity>> = habits
    override suspend fun setReminder(habitId: Long, time: String?) {
        habits.value = habits.value.map { if (it.id == habitId) it.copy(reminderTime = time) else it }
    }
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate) = emptySet<AchievementType>()
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?) =
        emptySet<AchievementType>()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override suspend fun reconcileAchievements() = emptySet<AchievementType>()
    override fun observeHabitWithStats(habitId: Long, today: java.time.LocalDate): kotlinx.coroutines.flow.Flow<com.habitly.app.data.repository.HabitWithStats?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

class RemindersViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun listsActiveHabitsInTheUsersOrder() = runTest {
        val vm = RemindersViewModel(FakeRemindersDataSource(listOf(habit(1, 2), habit(2, 1), habit(3, 0, archived = true))))
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L), vm.uiState.value.habits.map { it.id })
    }

    @Test fun settingAndClearingAReminderGoesThroughTheRepository() = runTest {
        val fake = FakeRemindersDataSource(listOf(habit(1, 1)))
        val vm = RemindersViewModel(fake)
        vm.onSetReminder(1, "07:30"); advanceUntilIdle()
        assertEquals("07:30", vm.uiState.value.habits.single().reminderTime)
        vm.onSetReminder(1, null); advanceUntilIdle()
        assertEquals(null, vm.uiState.value.habits.single().reminderTime)
    }
}
