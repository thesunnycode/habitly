package com.habitly.app.ui.achievements

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementGroup
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private class FakeHabitDataSource(progress: List<AchievementProgress>) : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    private val progressFlow = MutableStateFlow(progress)
    override fun observeHabitsWithStats(today: LocalDate) = MutableStateFlow(emptyList<HabitWithStats>()).asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> {
        lastIncremented = habitId to date
        return emptySet()
    }

    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: com.habitly.app.domain.CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        return emptySet()
    }
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = MutableStateFlow(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = progressFlow.asStateFlow()
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class AchievementsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun groupsAndCountsFollowTheRepositoryProgress() = runTest {
        val fake = FakeHabitDataSource(
            listOf(
                AchievementProgress(AchievementType.STREAK_3, unlockedAt = 1_000L, progress = 3),
                AchievementProgress(AchievementType.STREAK_7, unlockedAt = null, progress = 3),
                AchievementProgress(AchievementType.CHECKINS_10, unlockedAt = null, progress = 4),
            )
        )
        val viewModel = AchievementsViewModel(fake, LocalDate.of(2026, 9, 29))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(3, state.items.size)
        assertEquals(1, state.unlockedCount)
        assertEquals(16, state.total)
        assertEquals(
            listOf(AchievementType.STREAK_3, AchievementType.STREAK_7),
            state.groups.getValue(AchievementGroup.STREAKS).map { it.type },
        )
        assertEquals(listOf(AchievementType.CHECKINS_10), state.groups.getValue(AchievementGroup.VOLUME).map { it.type })
        assertTrue(state.groups.getValue(AchievementGroup.SPECIAL).isEmpty())
    }
}
