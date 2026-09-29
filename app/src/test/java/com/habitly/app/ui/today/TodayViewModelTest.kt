package com.habitly.app.ui.today

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.prefs.TodayPrefs
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val D: LocalDate = LocalDate.of(2026, 9, 29)

private class FakeHabitDataSource(initial: List<HabitWithStats> = emptyList()) : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, CompletionStatus?>? = null
    val habits = MutableStateFlow(initial)
    var lastToggledId: Long? = null
    var lastToggledDate: LocalDate? = null
    var lastQueriedDate: LocalDate? = null

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> {
        lastQueriedDate = today
        return habits.asStateFlow()
    }
    override suspend fun addHabit(habit: HabitEntity): Long = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        lastToggledId = habitId
        lastToggledDate = date
        return emptySet()
    }
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> {
        lastIncremented = habitId to date
        return emptySet()
    }
    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        lastToggledDate = date
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
    override suspend fun clearAchievements() {}
    override fun observeAllHabits(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeAllLogs(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitLogEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeRecordsForHabit(habitId: Long) = MutableStateFlow(emptyList<CompletionRecord>())
}

class FakeTodayPrefs(name: String? = null, celebrated: String? = null) : TodayPrefs {
    override val userName = MutableStateFlow(name)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val lastCelebratedDate = MutableStateFlow(celebrated)
    override suspend fun setLastCelebratedDate(date: String) { lastCelebratedDate.value = date }
}

private fun stats(
    id: Long,
    name: String,
    due: Boolean,
    done: Boolean,
    streak: Int = 0,
    levels: Map<LocalDate, Int> = emptyMap(),
) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#51CF66", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = streak,
    bestStreak = streak,
    completionRate = 0f,
    isCompletedToday = done,
    isDueToday = due,
    todayStatus = if (done) CompletionStatus.DONE else null,
    levels = levels,
)

class TodayViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loggingTodayClearsThatHabitsNotificationsButAPastDayDoesNot() = runTest {
        val cancelled = mutableListOf<Long>()
        val vm = TodayViewModel(
            FakeHabitDataSource(listOf(stats(id = 1, name = "Read", due = true, done = false))),
            FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON },
            notifications = { cancelled += it },
        )
        vm.onToggleHabit(1); vm.onIncrement(2); vm.onSetStatus(3, CompletionStatus.SKIPPED)
        vm.onSetStatus(4, null) // clearing a day is not a check-off
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), cancelled)

        cancelled.clear()
        vm.onSelectDate(D.minusDays(1)); vm.onToggleHabit(1); advanceUntilIdle()
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun splitsHabitsIntoDueDoneAndNotDue() = runTest {
        val repo = FakeHabitDataSource(listOf(
            stats(id = 1, name = "Water", due = true, done = false),
            stats(id = 2, name = "Read", due = true, done = true),
            stats(id = 3, name = "Gym", due = false, done = false),
        ))
        val vm = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(listOf(1L), s.due.map { it.habit.id })
        assertEquals(listOf(2L), s.done.map { it.habit.id })
        assertEquals(listOf(3L), s.notDue.map { it.habit.id })
        assertEquals(1, s.doneCount); assertEquals(2, s.dueCount)
    }

    @Test
    fun aRestDayCountsAsFinished() = runTest {
        val rested = stats(id = 1, name = "Run", due = true, done = false).copy(todayStatus = CompletionStatus.SKIPPED)
        val vm = TodayViewModel(FakeHabitDataSource(listOf(rested)), FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.uiState.value.done.map { it.habit.id })
        assertTrue(vm.uiState.value.due.isEmpty())
    }

    @Test
    fun flagsAStreakAtRiskOnlyInTheEvening() = runTest {
        val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Gym", due = true, done = false, streak = 4)))
        val noon = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
        val evening = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.of(19, 0) })
        advanceUntilIdle()
        assertNull(noon.uiState.value.atRisk)
        assertEquals(1L, evening.uiState.value.atRisk?.habit?.id)
    }

    @Test
    fun noStreakIsAtRiskWhileViewingAnEarlierDay() = runTest {
        val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Gym", due = true, done = false, streak = 4)))
        val vm = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.of(19, 0) })
        advanceUntilIdle()
        vm.onSelectDate(D.minusDays(1))
        advanceUntilIdle()
        assertNull(vm.uiState.value.atRisk)
    }

    @Test
    fun celebratesOncePerDayWhenEverythingIsDone() = runTest {
        val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Read", due = true, done = true)))
        val prefs = FakeTodayPrefs()
        val vm = TodayViewModel(repo, prefs, today = { D }, now = { LocalTime.NOON })
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showCelebration)
        vm.onCelebrationShown(); advanceUntilIdle()
        assertFalse(vm.uiState.value.showCelebration)
        assertEquals(D.toString(), prefs.lastCelebratedDate.value)
    }

    @Test
    fun exposesTheUserName() = runTest {
        val vm = TodayViewModel(FakeHabitDataSource(emptyList()), FakeTodayPrefs(name = "Sunny"), today = { D })
        advanceUntilIdle()
        assertEquals("Sunny", vm.uiState.value.userName)
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun dayCompletionIsTheShareOfDueHabitsFinishedThatDay() = runTest {
        val yesterday = D.minusDays(1)
        val repo = FakeHabitDataSource(listOf(
            stats(id = 1, name = "Water", due = true, done = false, levels = mapOf(yesterday to 4)),
            stats(id = 2, name = "Read", due = true, done = false),
        ))
        val vm = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
        advanceUntilIdle()
        assertEquals(0.5f, vm.uiState.value.dayCompletion[yesterday] ?: -1f, 0.001f)
        assertEquals(0f, vm.uiState.value.dayCompletion[D] ?: -1f, 0.001f)
    }

    @Test
    fun onToggleHabitTogglesTheSelectedDay() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { D })
        viewModel.onToggleHabit(42L)
        advanceUntilIdle()
        assertEquals(42L, fake.lastToggledId)
        assertEquals(D, fake.lastToggledDate)
    }

    @Test
    fun onIncrementCountsUpTowardsTheHabitsGoal() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { D })
        viewModel.onIncrement(42L)
        advanceUntilIdle()
        assertEquals(42L to D, fake.lastIncremented)
    }

    @Test
    fun theSelectedDayStartsAtTodayAndTheWeekEndsThere() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { today })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(today, state.selectedDate)
        assertEquals(7, state.weekDays.size)
        assertEquals(today, state.weekDays.last())
        assertEquals(LocalDate.of(2026, 8, 16), state.weekDays.first())
    }

    @Test
    fun selectingAnEarlierDayRequeriesTheRepositoryForThatDay() = runTest {
        // Logging a day you forgot is the whole point: the list has to show that day's state.
        val fake = FakeHabitDataSource()
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 20))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 20), viewModel.uiState.value.selectedDate)
        assertEquals(LocalDate.of(2026, 8, 20), fake.lastQueriedDate)
    }

    @Test
    fun togglingWhileViewingAnEarlierDayWritesToThatDay() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 19))
        viewModel.onToggleHabit(7L)
        viewModel.onIncrement(8L)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 19), fake.lastToggledDate)
        assertEquals(LocalDate.of(2026, 8, 19), fake.lastIncremented?.second)
    }

    @Test
    fun settingAStatusWhileViewingAnEarlierDayWritesToThatDay() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()

        viewModel.onSelectDate(LocalDate.of(2026, 8, 18))
        viewModel.onSetStatus(3L, CompletionStatus.SKIPPED)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 8, 18), fake.lastToggledDate)
        assertEquals(3L to CompletionStatus.SKIPPED, fake.lastSetStatus)
    }

    @Test
    fun aFutureDayCannotBeSelected() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { today })
        advanceUntilIdle()

        viewModel.onSelectDate(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun isViewingTodayIsFalseOnlyWhenAnEarlierDayIsSelected() = runTest {
        val fake = FakeHabitDataSource()
        val today = LocalDate.of(2026, 8, 22)
        val viewModel = TodayViewModel(fake, FakeTodayPrefs(), today = { today })
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isViewingToday)

        viewModel.onSelectDate(today.minusDays(2))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isViewingToday)
    }

    @Test
    fun noCelebrationWhileViewingAnEarlierDay() = runTest {
        val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Read", due = true, done = true)))
        val vm = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
        advanceUntilIdle()
        vm.onSelectDate(D.minusDays(2))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showCelebration)
    }
}
