package com.habitly.app.ui.onboarding

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
import com.habitly.app.domain.HabitTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private class FakeOnboardingStore : OnboardingStore {
    var name: String? = null
    var completed = false
    override suspend fun setUserName(name: String?) { this.name = name }
    override suspend fun setCompleted() { completed = true }
}

private class RecordingDataSource : HabitDataSource {
    val added = mutableListOf<HabitEntity>()
    override suspend fun addHabit(habit: HabitEntity): Long { added += habit; return added.size.toLong() }
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
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
    override fun observeHabitWithStats(habitId: Long, today: java.time.LocalDate): kotlinx.coroutines.flow.Flow<com.habitly.app.data.repository.HabitWithStats?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

class OnboardingViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun finishingCreatesTheSelectedHabitsAndSavesTheName() = runTest {
        val repo = RecordingDataSource(); val store = FakeOnboardingStore()
        val vm = OnboardingViewModel(repo, store, now = { 1000L })
        vm.onNameChange("Sunny"); vm.onToggleTemplate("drink_water"); vm.onToggleTemplate("meditate")
        vm.onFinish(); advanceUntilIdle()
        assertEquals(listOf("Drink water", "Meditate"), repo.added.map { it.name })
        assertEquals("Sunny", store.name); assertTrue(store.completed); assertTrue(vm.uiState.value.isFinished)
    }

    @Test fun skipCompletesWithoutHabits() = runTest {
        val repo = RecordingDataSource(); val store = FakeOnboardingStore()
        val vm = OnboardingViewModel(repo, store)
        vm.onSkip(); advanceUntilIdle()
        assertTrue(repo.added.isEmpty()); assertTrue(store.completed)
    }

    @Test fun togglingTwiceDeselects() = runTest {
        val vm = OnboardingViewModel(RecordingDataSource(), FakeOnboardingStore())
        vm.onToggleTemplate("drink_water"); vm.onToggleTemplate("drink_water")
        assertTrue(vm.uiState.value.selected.isEmpty())
    }

    @Test fun onboardingKeysAllExist() {
        ONBOARDING_TEMPLATE_KEYS.forEach { assertNotNull(it, HabitTemplates.byKey(it)) }
        assertEquals(8, ONBOARDING_TEMPLATE_KEYS.size)
    }

    @Test fun stepsAdvanceAndGoBack() = runTest {
        val vm = OnboardingViewModel(RecordingDataSource(), FakeOnboardingStore())
        vm.onNext(); vm.onNext(); assertEquals(2, vm.uiState.value.step)
        vm.onBack(); assertEquals(1, vm.uiState.value.step)
    }
}
