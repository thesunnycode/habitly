package com.habitly.app.ml

import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

private class FakeData(habits: List<HabitEntity>, logs: List<HabitLogEntity>) : HabitDataSource {
    val habits = MutableStateFlow(habits)
    val logs = MutableStateFlow(logs)
    override fun observeAllHabits(): Flow<List<HabitEntity>> = habits
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = logs

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> = flowOf(null)
    override suspend fun addHabit(habit: HabitEntity): Long = 0L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> = emptySet()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override suspend fun reconcileAchievements(): Set<AchievementType> = emptySet()
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

class ForecastRepositoryTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val now = 1_790_000_000_000L
    private val hour = 3_600_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val stored = MutableStateFlow<String?>(null)
    private val history = SyntheticHistory.mondaySkipper(today, 120, 3, Random(42))
    private val data = FakeData(history.first, history.second)

    private fun TestScope.repo() = ForecastRepository(
        data, stored, { stored.value = it },
        today = { today }, clock = { now }, zone = ZoneOffset.UTC,
        compute = StandardTestDispatcher(testScheduler),
    )

    private fun decoded(): RiskModel = json.decodeFromString(RiskModel.serializer(), stored.value!!)

    private fun encode(model: RiskModel) = json.encodeToString(RiskModel.serializer(), model)

    @Test fun refreshWithNothingStoredTrainsAndSaves() = runTest {
        repo().refreshIfStale()
        assertNotNull(stored.value)
        val model = decoded()
        assertFalse(model.isStarter)
        assertEquals(now, model.trainedAt)
    }

    @Test fun freshModelIsKeptAndStaleOneRetrained() = runTest {
        val fresh = encode(RiskModel.starter(now - hour))
        stored.value = fresh
        repo().refreshIfStale()
        assertEquals(fresh, stored.value)

        stored.value = encode(RiskModel.starter(now - 25 * hour))
        repo().refreshIfStale()
        assertEquals(now, decoded().trainedAt)
        assertFalse(decoded().isStarter)
    }

    @Test fun observeTodayReEmitsWhenLogsChange() = runTest {
        val emissions = mutableListOf<Forecast>()
        val job = launch { repo().observeToday().collect { emissions += it } }
        advanceUntilIdle()
        val first = emissions.last().items.first()
        data.logs.value = data.logs.value +
            HabitLogEntity(habitId = first.habitId, date = today.toString(), status = CompletionStatus.DONE, completedAt = null, note = null)
        advanceUntilIdle()
        assertTrue(emissions.size >= 2)
        assertTrue(emissions.last().items.none { it.habitId == first.habitId })
        job.cancel()
    }

    @Test fun corruptStoredJsonFallsBackToTheStarter() = runTest {
        stored.value = "{not json"
        val forecast = repo().observeToday().first()
        assertTrue(forecast.isLearning)
        assertEquals(3, forecast.items.size)
    }

    @Test fun todayRisksMapsHabitIdToProbability() = runTest {
        val r = repo()
        val expected = r.observeToday().first().items.associate { it.habitId to it.probability }
        assertEquals(3, expected.size)
        assertEquals(expected, r.todayRisks())
    }
}
