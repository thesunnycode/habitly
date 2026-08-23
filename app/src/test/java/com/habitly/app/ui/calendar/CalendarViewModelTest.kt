package com.habitly.app.ui.calendar

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private class FakeHabitDataSource(
    private val habits: List<HabitWithStats>,
    private val recordsByHabit: Map<Long, List<CompletionRecord>>,
) : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    override fun observeHabitsWithStats(today: LocalDate) = MutableStateFlow(habits).asStateFlow()
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
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long) = MutableStateFlow(recordsByHabit[habitId] ?: emptyList()).asStateFlow()
}

private fun habit(id: Long) = HabitWithStats(
    habit = HabitEntity(id, "H$id", "star", "#000", "General", FrequencyType.DAILY, null, null, null, 0L, false),
    currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
)

class CalendarViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mergedRecordsMarksADayCompletedIfAnyHabitWasCompletedThatDay() = runTest {
        val day = LocalDate.of(2026, 8, 20)
        val fake = FakeHabitDataSource(
            habits = listOf(habit(1), habit(2)),
            recordsByHabit = mapOf(
                1L to listOf(CompletionRecord(day, false)),
                2L to listOf(CompletionRecord(day, true)),
            ),
        )
        val viewModel = CalendarViewModel(fake)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mergedRecords.first { it.date == day }.completed)
    }

    @Test
    fun onMonthChangeUpdatesTheDisplayedMonth() = runTest {
        val fake = FakeHabitDataSource(habits = emptyList(), recordsByHabit = emptyMap())
        val viewModel = CalendarViewModel(fake)
        advanceUntilIdle()
        val target = java.time.YearMonth.of(2026, 6)
        viewModel.onMonthChange(target)
        assertEquals(target, viewModel.uiState.value.month)
    }
}
