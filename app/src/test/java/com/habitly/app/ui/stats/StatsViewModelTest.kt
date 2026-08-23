package com.habitly.app.ui.stats

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private fun habit(id: Long, name: String, rate: Float, last7Days: List<Boolean> = List(7) { false }) = HabitWithStats(
    habit = HabitEntity(id, name, "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = rate, isCompletedToday = false, last7Days = last7Days,
)

private class FakeHabitDataSource(habits: List<HabitWithStats>) : HabitDataSource {
    private val flow = MutableStateFlow(habits)
    override fun observeHabitsWithStats(today: LocalDate) = flow.asStateFlow()
    override suspend fun addHabit(habit: HabitEntity) = 1L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = MutableStateFlow(emptyList())
}

class StatsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun identifiesBestAndWorstHabitByCompletionRate() = runTest {
        val fake = FakeHabitDataSource(listOf(habit(1, "Water", 0.9f), habit(2, "Gym", 0.2f), habit(3, "Read", 0.5f)))
        val viewModel = StatsViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals("Water", viewModel.uiState.value.bestHabitName)
        assertEquals("Gym", viewModel.uiState.value.worstHabitName)
    }

    @Test
    fun overallCompletionRateIsTheMeanAcrossHabits() = runTest {
        val fake = FakeHabitDataSource(listOf(habit(1, "A", 1.0f), habit(2, "B", 0.0f)))
        val viewModel = StatsViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals(0.5f, viewModel.uiState.value.overallCompletionRate, 0.001f)
    }

    @Test
    fun withNoHabitsBestAndWorstAreNull() = runTest {
        val viewModel = StatsViewModel(FakeHabitDataSource(emptyList()), today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.bestHabitName)
        assertEquals(null, viewModel.uiState.value.worstHabitName)
    }

    @Test
    fun weeklyCompletionByDayReflectsTheRealPerDayCompletionRateAcrossHabits() = runTest {
        // Day index 0 = both habits done (rate 1.0), day index 3 = only one of two done (rate 0.5)
        val fake = FakeHabitDataSource(
            listOf(
                habit(1, "A", 0.5f, last7Days = List(7) { it == 0 || it == 3 }),
                habit(2, "B", 0.5f, last7Days = List(7) { it == 0 }),
            )
        )
        val viewModel = StatsViewModel(fake, today = { LocalDate.of(2026, 8, 22) })
        advanceUntilIdle()
        val daily = viewModel.uiState.value.weeklyCompletionByDay
        assertEquals(1.0f, daily[0], 0.001f)
        assertEquals(0.5f, daily[3], 0.001f)
        assertEquals(0.0f, daily[1], 0.001f)
    }
}
