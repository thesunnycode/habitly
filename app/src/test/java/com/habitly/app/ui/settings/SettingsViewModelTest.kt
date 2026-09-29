package com.habitly.app.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate

private fun habit(id: Long) = HabitWithStats(
    habit = HabitEntity(id, "H$id", "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

private class FakeHabitDataSource(
    habits: List<HabitWithStats>,
    private val archived: List<HabitEntity> = emptyList(),
    private val logs: List<HabitLogEntity> = emptyList(),
) : HabitDataSource {
    var achievementsCleared = false
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    private val flow = MutableStateFlow(habits)
    val deleted = mutableListOf<Long>()
    override fun observeHabitsWithStats(today: LocalDate) = flow.asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) { deleted.add(habit.id) }
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
    override fun observeAchievementProgress(today: LocalDate): Flow<List<com.habitly.app.data.repository.AchievementProgress>> = MutableStateFlow(emptyList())
    override suspend fun reconcileAchievements() = emptySet<com.habitly.app.domain.AchievementType>()
    override suspend fun clearAchievements() { achievementsCleared = true }
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(flow.value.map { it.habit } + archived)
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(logs)
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onThemeModeChangePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = SettingsViewModel(prefs, FakeHabitDataSource(emptyList()))
        viewModel.onThemeModeChange(ThemeMode.DARK).join()
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun onResetAllDataDeletesEveryHabitIncludingArchivedAndClearsAwards() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val fake = FakeHabitDataSource(listOf(habit(1), habit(2)), archived = listOf(habit(3).habit.copy(isArchived = true)))
        val viewModel = SettingsViewModel(prefs, fake)
        advanceUntilIdle()
        viewModel.onResetAllData()
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L), fake.deleted.toSet())
        assertTrue(fake.achievementsCleared)
        assertTrue(viewModel.uiState.value.resetCompleted)
    }

    @Test
    fun themeDefaultsToDarkBeforePrefsLoad() {
        assertEquals(ThemeMode.DARK, SettingsUiState().themeMode)
    }

    @Test
    fun onWeekStartChangePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = SettingsViewModel(prefs, FakeHabitDataSource(emptyList()))
        viewModel.onWeekStartChange(DayOfWeek.SUNDAY).join()
        advanceUntilIdle()
        assertEquals(DayOfWeek.SUNDAY, prefs.weekStart.first())
        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.weekStart)
    }

    @Test
    fun onStreakNudgeTogglePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = SettingsViewModel(prefs, FakeHabitDataSource(emptyList()))
        viewModel.onStreakNudgeToggle(false).join()
        advanceUntilIdle()
        assertFalse(prefs.streakNudgeEnabled.first())
        assertFalse(viewModel.uiState.value.streakNudgeEnabled)
    }

    @Test
    fun onExportReturnsCsvOfEveryHabitAndLog() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val fake = FakeHabitDataSource(
            listOf(habit(1)),
            logs = listOf(HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, null, 0)),
        )
        val viewModel = SettingsViewModel(prefs, fake)
        val csv = viewModel.onExport()
        assertEquals("habit,category,date,status,progress,unit,note\nH1,General,2026-09-28,DONE,0,,\n", csv)
    }

    @Test
    fun onLoadDemoDataRunsTheSeeder() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        var seeded = 0
        val viewModel = SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), loadDemoData = { seeded++ })
        viewModel.onLoadDemoData()
        advanceUntilIdle()
        assertEquals(1, seeded)
    }

    @Test
    fun versionAndBuildTypeAreExposed() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), versionName = "9.9", isDebug = true)
        assertEquals("9.9", viewModel.uiState.value.versionName)
        assertTrue(viewModel.uiState.value.isDebug)
    }
}
