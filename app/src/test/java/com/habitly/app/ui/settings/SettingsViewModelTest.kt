package com.habitly.app.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.habitly.app.MainDispatcherRule
import com.habitly.app.ai.AiPrefs
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

private fun aiPrefs() = AiPrefs(ApplicationProvider.getApplicationContext())

private fun habit(id: Long) = HabitWithStats(
    habit = HabitEntity(id, "H$id", "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

private class FakeHabitDataSource(
    habits: List<HabitWithStats>,
    private val archived: List<HabitEntity> = emptyList(),
    private val logs: List<HabitLogEntity> = emptyList(),
) : HabitDataSource {
    var resetCalled = false
    /** When set, the reset waits here, standing in for a slow database. */
    var resetGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
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
    override fun observeHabitWithStats(habitId: Long, today: java.time.LocalDate): kotlinx.coroutines.flow.Flow<com.habitly.app.data.repository.HabitWithStats?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun resetAll() { resetGate?.await(); resetCalled = true }
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(flow.value.map { it.habit } + archived)
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(logs)
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    /**
     * Builds the ViewModel and waits until it has read every pref flow once. Each flow's first read opens the
     * DataStore file on an IO thread, and on Windows a write that renames the file over an open read fails, so
     * tests write only after this. The theme is the marker: SYSTEM differs from the state's DARK default.
     */
    private suspend fun settled(prefs: SettingsPrefs, build: () -> SettingsViewModel): SettingsViewModel {
        prefs.setThemeMode(ThemeMode.SYSTEM)
        return build().also { viewModel -> viewModel.uiState.first { it.themeMode == ThemeMode.SYSTEM } }
    }

    @Test
    fun onThemeModeChangePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs()) }
        viewModel.onThemeModeChange(ThemeMode.DARK).join()
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun onResetAllDataResetsTheDatabaseAndClearsEveryHabitsNotifications() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val fake = FakeHabitDataSource(listOf(habit(1), habit(2)), archived = listOf(habit(3).habit.copy(isArchived = true)))
        prefs.markBadgesSeen(setOf("STREAK_7"))
        prefs.setLastCelebratedDate("2026-09-29")
        val cancelled = mutableListOf<Long>()
        val viewModel = settled(prefs) { SettingsViewModel(prefs, fake, aiPrefs(), notifications = { cancelled += it }) }
        advanceUntilIdle()
        viewModel.onResetAllData()
        // The prefs writes run on DataStore's own IO thread, so wait for the flag rather than idling.
        viewModel.uiState.first { it.resetCompleted }
        assertTrue("habits, archived ones, logs and badges go in one reset", fake.resetCalled)
        assertEquals("every deleted habit's notifications are cleared", setOf(1L, 2L, 3L), cancelled.toSet())
        assertTrue("a fresh start celebrates badges again", prefs.seenBadgeKeys.first().isEmpty())
        assertEquals(null, prefs.lastCelebratedDate.first())
        assertTrue(viewModel.uiState.value.resetCompleted)
    }

    @Test
    fun leavingSettingsMidResetStillFinishesTheReset() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        prefs.markBadgesSeen(setOf("STREAK_7"))
        prefs.setLastCelebratedDate("2026-09-29")
        val fake = FakeHabitDataSource(listOf(habit(1)))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fake.resetGate = gate
        val cancelled = mutableListOf<Long>()
        val store = androidx.lifecycle.ViewModelStore()
        val viewModel = settled(prefs) {
            androidx.lifecycle.ViewModelProvider(
                store,
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        SettingsViewModel(prefs, fake, aiPrefs(), notifications = { cancelled += it }) as T
                },
            )[SettingsViewModel::class.java]
        }
        advanceUntilIdle()

        val reset = viewModel.onResetAllData()
        advanceUntilIdle()
        store.clear() // the user left Settings; viewModelScope is cancelled
        gate.complete(Unit)
        // A cancelled job still completes only once its NonCancellable block has returned, so every prefs
        // write is done before the reads below (a read racing a DataStore write fails on Windows).
        reset.join()

        assertTrue(fake.resetCalled)
        assertEquals(listOf(1L), cancelled)
        // The prefs half ran too.
        assertTrue(prefs.seenBadgeKeys.first().isEmpty())
        assertEquals(null, prefs.lastCelebratedDate.first())
    }

    @Test
    fun theResetConfirmationIsConsumedOnceShown() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs()) }
        advanceUntilIdle()
        assertFalse("nothing to confirm before a reset", viewModel.uiState.value.resetCompleted)
        viewModel.onResetAllData()
        viewModel.uiState.first { it.resetCompleted }
        viewModel.onResetMessageShown()
        assertFalse(viewModel.uiState.value.resetCompleted)
    }

    @Test
    fun themeDefaultsToDarkBeforePrefsLoad() {
        assertEquals(ThemeMode.DARK, SettingsUiState().themeMode)
    }

    @Test
    fun onWeekStartChangePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs()) }
        viewModel.onWeekStartChange(DayOfWeek.SUNDAY).join()
        advanceUntilIdle()
        assertEquals(DayOfWeek.SUNDAY, prefs.weekStart.first())
        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.weekStart)
    }

    @Test
    fun onStreakNudgeTogglePersistsAndReflectsInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs()) }
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
        val viewModel = settled(prefs) { SettingsViewModel(prefs, fake, aiPrefs()) }
        val csv = viewModel.onExport()
        assertEquals("habit,category,date,status,progress,unit,note\nH1,General,2026-09-28,DONE,0,,\n", csv)
    }

    @Test
    fun onLoadDemoDataRunsTheSeeder() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        var seeded = 0
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs(), loadDemoData = { seeded++ }) }
        viewModel.onLoadDemoData()
        advanceUntilIdle()
        assertEquals(1, seeded)
    }

    @Test
    fun versionAndBuildTypeAreExposed() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), aiPrefs(), versionName = "9.9", isDebug = true) }
        assertEquals("9.9", viewModel.uiState.value.versionName)
        assertTrue(viewModel.uiState.value.isDebug)
    }

    @Test
    fun aiTogglesPersistAndReflectInUiState() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val ai = aiPrefs()
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), ai) }
        viewModel.onAiEnabledToggle(false).join()
        viewModel.onShowForecastToggle(false).join()
        viewModel.onDemoAiToggle(true).join()
        assertFalse(ai.aiEnabled.first())
        assertFalse(ai.showForecast.first())
        assertTrue(ai.demoAi.first())
        val state = viewModel.uiState.first { !it.aiEnabled && !it.showForecast && it.demoAi }
        assertTrue("Demo AI already on keeps its switch visible", state.demoAiRevealed)
    }

    @Test
    fun theFifthTapOnTheAiHeadingRevealsDemoAi() = runTest {
        val prefs = SettingsPrefs(ApplicationProvider.getApplicationContext())
        val ai = aiPrefs()
        ai.setDemoAi(false)
        val viewModel = settled(prefs) { SettingsViewModel(prefs, FakeHabitDataSource(emptyList()), ai) }
        advanceUntilIdle()
        repeat(4) { viewModel.onAiHeaderTapped() }
        assertFalse("four taps are not enough", viewModel.uiState.value.demoAiRevealed)
        viewModel.onAiHeaderTapped()
        assertTrue(viewModel.uiState.value.demoAiRevealed)
        assertFalse("revealing the switch does not turn Demo AI on", ai.demoAi.first())
    }
}
