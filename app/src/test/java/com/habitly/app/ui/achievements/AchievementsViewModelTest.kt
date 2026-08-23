package com.habitly.app.ui.achievements

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
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

private class FakeHabitDataSource(unlocked: List<AchievementEntity>) : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    private val flow = MutableStateFlow(unlocked)
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
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flow.asStateFlow()
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class AchievementsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun itemsCoverEveryAchievementTypeWithCorrectUnlockedFlag() = runTest {
        val fake = FakeHabitDataSource(listOf(AchievementEntity(1, null, "STREAK_7", 0L)))
        val viewModel = AchievementsViewModel(fake)
        advanceUntilIdle()
        val items = viewModel.uiState.value.items
        assertEquals(AchievementType.entries.size, items.size)
        assertTrue(items.first { it.type == AchievementType.STREAK_7 }.unlocked)
        assertEquals(false, items.first { it.type == AchievementType.STREAK_30 }.unlocked)
    }
}
