package com.habitly.app.ui.you

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.prefs.YouPrefs
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private class FakeYouPrefs(name: String?) : YouPrefs {
    override val userName = MutableStateFlow(name)
    override suspend fun setUserName(name: String?) { userName.value = name }
}

private class FakeYouDataSource(
    private val habits: List<HabitEntity>,
    private val logs: List<HabitLogEntity>,
    private val progress: List<AchievementProgress> = emptyList(),
) : HabitDataSource {
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(habits)
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(logs)
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(progress)
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
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override suspend fun reconcileAchievements() = emptySet<AchievementType>()
    override suspend fun clearAchievements() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

private val CREATED = LocalDate.of(2026, 8, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun fakeWithTwoDoneDays(progress: List<AchievementProgress> = emptyList()) = FakeYouDataSource(
    habits = listOf(
        HabitEntity(1, "Read", "star", "#FF922B", "Mind", FrequencyType.DAILY, null, null, null, CREATED, false),
    ),
    logs = listOf(
        HabitLogEntity(0, 1, "2026-09-27", CompletionStatus.DONE, 1L, null, 0),
        HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, null, 0),
    ),
    progress = progress,
)

class YouViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(source: HabitDataSource, prefs: YouPrefs) = YouViewModel(
        source, prefs, today = { LocalDate.of(2026, 9, 29) }, computeDispatcher = mainDispatcherRule.dispatcher,
    )

    @Test fun summarisesLifetimeNumbers() = runTest {
        val vm = viewModel(fakeWithTwoDoneDays(), FakeYouPrefs("Sunny"))
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("Sunny", s.name); assertEquals(2, s.checkIns); assertEquals(1, s.activeHabits)
        assertEquals(2, s.bestStreak)
        assertEquals(YearMonth.of(2026, 8), s.memberSince)
    }

    @Test fun renamingSavesThroughPrefs() = runTest {
        val prefs = FakeYouPrefs(null)
        val vm = viewModel(fakeWithTwoDoneDays(), prefs)
        vm.onNameChange("Ada"); advanceUntilIdle()
        assertEquals("Ada", prefs.userName.value)
        assertEquals("Ada", vm.uiState.value.name)
    }

    @Test fun recentBadgesAreTheLastThreeUnlockedAndNextIsTheClosestLocked() = runTest {
        val progress = listOf(
            AchievementProgress(AchievementType.FIRST_HABIT, unlockedAt = 100L, progress = 1),
            AchievementProgress(AchievementType.STREAK_3, unlockedAt = 400L, progress = 3),
            AchievementProgress(AchievementType.CHECKINS_10, unlockedAt = 300L, progress = 10),
            AchievementProgress(AchievementType.PERFECT_DAY, unlockedAt = 200L, progress = 1),
            AchievementProgress(AchievementType.STREAK_7, unlockedAt = null, progress = 3), // 3/7
            AchievementProgress(AchievementType.CHECKINS_100, unlockedAt = null, progress = 60), // 60/100
        )
        val vm = viewModel(fakeWithTwoDoneDays(progress), FakeYouPrefs(null))
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(
            listOf(AchievementType.STREAK_3, AchievementType.CHECKINS_10, AchievementType.PERFECT_DAY),
            s.recentBadges.map { it.type },
        )
        assertEquals(AchievementType.CHECKINS_100, s.nextBadge?.type)
        assertEquals(4, s.unlockedCount)
    }

    @Test fun withNoHabitsThereIsNoMemberSince() = runTest {
        val vm = viewModel(FakeYouDataSource(emptyList(), emptyList()), FakeYouPrefs(null))
        advanceUntilIdle()
        assertNull(vm.uiState.value.memberSince)
        assertEquals(0, vm.uiState.value.checkIns)
    }
}
