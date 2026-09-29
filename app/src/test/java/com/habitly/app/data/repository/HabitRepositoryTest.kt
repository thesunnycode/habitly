package com.habitly.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HabitRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
    }

    @After
    fun teardown() { db.close() }

    private fun newHabit(name: String = "Meditate") = com.habitly.app.data.local.HabitEntity(
        id = 0, name = name, icon = "spa", colorHex = "#6B9080", category = "Wellness",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null,
        reminderTime = null, createdAt = 0L, isArchived = false,
    )

    @Test
    fun observeHabitsWithStatsReflectsAFreshHabitAsNotCompletedToday() = runBlocking {
        repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        val list = repo.observeHabitsWithStats(today).first()
        assertEquals(1, list.size)
        assertEquals(false, list.first().isCompletedToday)
        assertEquals(0, list.first().currentStreak)
    }

    @Test
    fun toggleCompletionMarksTodayCompleteAndUpdatesStreak() = runBlocking {
        val id = repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        repo.toggleCompletion(id, today, null)
        val list = repo.observeHabitsWithStats(today).first()
        assertTrue(list.first().isCompletedToday)
        assertEquals(1, list.first().currentStreak)
    }

    @Test
    fun togglingCompletionTwiceUnmarksIt() = runBlocking {
        val id = repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        repo.toggleCompletion(id, today, null)
        repo.toggleCompletion(id, today, null)
        val list = repo.observeHabitsWithStats(today).first()
        assertEquals(false, list.first().isCompletedToday)
    }

    @Test
    fun addingTheFirstHabitUnlocksFirstStep() = runBlocking {
        repo.addHabit(newHabit())
        val stored = repo.observeAchievements().first()
        assertEquals(listOf(AchievementType.FIRST_HABIT.name), stored.map { it.type })
        assertNull(stored.single().habitId)
    }

    /** Writes a habit with three done days straight to the DAOs, so nothing has been evaluated yet. */
    private suspend fun insertUnevaluatedHistory() {
        val id = db.habitDao().insert(newHabit())
        (20..22).forEach { day ->
            db.habitLogDao().upsert(
                com.habitly.app.data.local.HabitLogEntity(0, id, "2026-08-$day", CompletionStatus.DONE, 1L, null)
            )
        }
    }

    @Test
    fun reconcileStoresBadgesEarnedButNeverUnlocked() = runBlocking {
        insertUnevaluatedHistory()
        val stored = repo.reconcileAchievements()
        assertTrue(AchievementType.FIRST_HABIT in stored && AchievementType.STREAK_3 in stored)
        assertTrue(repo.reconcileAchievements().isEmpty())
    }

    @Test
    fun concurrentEvaluationsStoreEachBadgeOnce() = runBlocking {
        insertUnevaluatedHistory()
        // Two repositories, as screens and workers each build their own.
        val other = HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
        val jobs = List(4) { i ->
            async(Dispatchers.IO) { (if (i % 2 == 0) repo else other).reconcileAchievements() }
        }
        jobs.awaitAll()
        val types = repo.observeAchievements().first().map { it.type }
        assertEquals(types.distinct(), types)
        assertTrue(AchievementType.STREAK_3.name in types)
    }

    @Test
    fun recordingARestDayUnlocksSecondChance() = runBlocking {
        val id = repo.addHabit(newHabit())
        val unlocked = repo.setStatus(id, LocalDate.of(2026, 8, 22), CompletionStatus.SKIPPED, null)
        assertTrue(AchievementType.SECOND_CHANCE in unlocked)
    }

    @Test
    fun achievementProgressCoversEveryBadgeWithUnlockTimesAndProgress() = runBlocking {
        val id = repo.addHabit(newHabit())
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 21), null)
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 22), null)
        val progress = repo.observeAchievementProgress(LocalDate.of(2026, 8, 23)).first()
        assertEquals(16, progress.size)
        val first = progress.first { it.type == AchievementType.FIRST_HABIT }
        assertTrue(first.unlockedAt != null)
        val streak3 = progress.first { it.type == AchievementType.STREAK_3 }
        assertNull(streak3.unlockedAt)
        assertEquals(2, streak3.progress)
        assertEquals(2, progress.first { it.type == AchievementType.CHECKINS_10 }.progress)
    }

    @Test
    fun toggleCompletionReturnsStreak7WhenBestStreakReachesSeven() = runBlocking {
        val id = repo.addHabit(newHabit())
        var day = LocalDate.of(2026, 8, 15)
        var unlocked = emptySet<AchievementType>()
        repeat(7) {
            unlocked = repo.toggleCompletion(id, day, null)
            day = day.plusDays(1)
        }
        assertTrue(AchievementType.STREAK_7 in unlocked)
    }

    @Test
    fun unTogglingADayClearsTheRecordInsteadOfStoringAMiss() = runBlocking {
        // A stored MISSED would break the streak; an accidental double tap must not do that.
        val id = repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        repo.toggleCompletion(id, today, null)
        repo.toggleCompletion(id, today, null)
        assertEquals(0, db.habitLogDao().observeLogsForHabit(id).first().size)
    }

    @Test
    fun setStatusRecordsASkippedDayThatLeavesTheStreakIntact() = runBlocking {
        val id = repo.addHabit(newHabit())
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 20), null)
        repo.setStatus(id, LocalDate.of(2026, 8, 21), CompletionStatus.SKIPPED, null)
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 22), null)

        val stats = repo.observeHabitsWithStats(LocalDate.of(2026, 8, 22)).first().first()
        assertEquals(CompletionStatus.SKIPPED, stats.last7DaysStatus[5])
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun setStatusWithNullClearsTheDay() = runBlocking {
        val id = repo.addHabit(newHabit())
        val day = LocalDate.of(2026, 8, 21)
        repo.setStatus(id, day, CompletionStatus.MISSED, null)
        repo.setStatus(id, day, null, null)
        assertEquals(0, db.habitLogDao().observeLogsForHabit(id).first().size)
    }

    @Test
    fun aMissedScheduledDayIsReportedAsTheStreakBreakPoint() = runBlocking {
        val id = repo.addHabit(newHabit())
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 20), null)
        repo.setStatus(id, LocalDate.of(2026, 8, 21), CompletionStatus.MISSED, null)
        repo.toggleCompletion(id, LocalDate.of(2026, 8, 22), null)

        val stats = repo.observeHabitsWithStats(LocalDate.of(2026, 8, 22)).first().first()
        assertEquals(LocalDate.of(2026, 8, 21), stats.lastBrokenDueDay)
    }

    private fun goalHabit(target: Int) = newHabit("Drink water").copy(
        targetCount = target,
        unit = "cups",
    )

    @Test
    fun incrementingAGoalHabitRecordsPartialProgressUntilTheTargetIsMet() = runBlocking {
        val id = repo.addHabit(goalHabit(3))
        val today = LocalDate.of(2026, 8, 22)

        repo.incrementProgress(id, today)
        var stats = repo.observeHabitsWithStats(today).first().first()
        assertEquals(1, stats.todayProgress)
        assertEquals(CompletionStatus.PARTIAL, stats.todayStatus)
        assertEquals(false, stats.isCompletedToday)

        repo.incrementProgress(id, today)
        repo.incrementProgress(id, today)
        stats = repo.observeHabitsWithStats(today).first().first()
        assertEquals(3, stats.todayProgress)
        assertEquals(CompletionStatus.DONE, stats.todayStatus)
        assertTrue(stats.isCompletedToday)
    }

    @Test
    fun partialProgressDoesNotCountTowardsTheStreak() = runBlocking {
        val id = repo.addHabit(goalHabit(3))
        val today = LocalDate.of(2026, 8, 22)
        repo.incrementProgress(id, today)
        assertEquals(0, repo.observeHabitsWithStats(today).first().first().currentStreak)
    }

    @Test
    fun tappingPastAMetGoalClearsTheDay() = runBlocking {
        val id = repo.addHabit(goalHabit(2))
        val today = LocalDate.of(2026, 8, 22)
        repo.incrementProgress(id, today)
        repo.incrementProgress(id, today)
        repo.incrementProgress(id, today)

        val stats = repo.observeHabitsWithStats(today).first().first()
        assertEquals(0, stats.todayProgress)
        assertEquals(null, stats.todayStatus)
    }

    @Test
    fun incrementingAHabitWithNoGoalJustTicksItOff() = runBlocking {
        val id = repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        repo.incrementProgress(id, today)
        assertTrue(repo.observeHabitsWithStats(today).first().first().isCompletedToday)
    }

    @Test
    fun markingAGoalHabitDoneOutrightFillsItsCounter() = runBlocking {
        val id = repo.addHabit(goalHabit(4))
        val today = LocalDate.of(2026, 8, 22)
        repo.setStatus(id, today, CompletionStatus.DONE, null)
        assertEquals(4, repo.observeHabitsWithStats(today).first().first().todayProgress)
    }

    @Test
    fun aPartialDayInThePastBreaksTheStreakLikeAMiss() = runBlocking {
        val id = repo.addHabit(goalHabit(3))
        repo.setStatus(id, LocalDate.of(2026, 8, 20), CompletionStatus.DONE, null)
        repo.incrementProgress(id, LocalDate.of(2026, 8, 21))
        repo.setStatus(id, LocalDate.of(2026, 8, 22), CompletionStatus.DONE, null)

        val stats = repo.observeHabitsWithStats(LocalDate.of(2026, 8, 22)).first().first()
        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun archivedHabitsLeaveTheActiveListAndCanBeRestored() = runBlocking {
        val id = repo.addHabit(newHabit("Read"))
        repo.setArchived(id, true)
        assertTrue(repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().none { it.habit.id == id })
        assertEquals(listOf(id), repo.observeArchivedHabits().first().map { it.id })
        repo.setArchived(id, false)
        assertTrue(repo.observeArchivedHabits().first().isEmpty())
    }

    @Test
    fun reorderPersistsTheGivenOrder() = runBlocking {
        val a = repo.addHabit(newHabit("A"))
        val b = repo.addHabit(newHabit("B"))
        val c = repo.addHabit(newHabit("C"))
        repo.reorder(listOf(c, a, b))
        val names = repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().map { it.habit.name }
        assertEquals(listOf("C", "A", "B"), names)
    }

    @Test
    fun newHabitsAreAppendedToTheEnd() = runBlocking {
        repo.addHabit(newHabit("A"))
        repo.addHabit(newHabit("B"))
        val orders = repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().map { it.habit.sortOrder }
        assertEquals(listOf(1, 2), orders)
    }

    @Test
    fun setReminderUpdatesOnlyTheTime() = runBlocking {
        val id = repo.addHabit(newHabit("Read"))
        repo.setReminder(id, "21:30")
        assertEquals("21:30", repo.getHabit(id)?.reminderTime)
        repo.setReminder(id, null)
        assertNull(repo.getHabit(id)?.reminderTime)
    }

    @Test
    fun levelsCoverTheLast53WeeksWithADoneDayAtFullStrength() = runBlocking {
        val id = repo.addHabit(newHabit("Read"))
        val today = LocalDate.of(2026, 9, 29)
        repo.setStatus(id, today.minusDays(1), CompletionStatus.DONE)
        repo.setStatus(id, today.minusDays(2), CompletionStatus.SKIPPED)
        repo.setStatus(id, today.minusWeeks(53), CompletionStatus.DONE)
        repo.setStatus(id, today.minusWeeks(53).minusDays(1), CompletionStatus.DONE)
        val levels = repo.observeHabitsWithStats(today).first().single().levels
        assertEquals(4, levels[today.minusDays(1)])
        assertEquals(1, levels[today.minusDays(2)])
        assertEquals(4, levels[today.minusWeeks(53)])
        assertNull(levels[today.minusWeeks(53).minusDays(1)])
        assertEquals(3, levels.size)
    }

    @Test
    fun observeAllHabitsIncludesArchivedAndObserveAllLogsCoversEveryHabit() = runBlocking {
        val a = repo.addHabit(newHabit("A"))
        val b = repo.addHabit(newHabit("B"))
        repo.setArchived(b, true)
        repo.setStatus(a, LocalDate.of(2026, 9, 28), CompletionStatus.DONE)
        repo.setStatus(b, LocalDate.of(2026, 9, 27), CompletionStatus.DONE)
        assertEquals(setOf(a, b), repo.observeAllHabits().first().map { it.id }.toSet())
        assertEquals(setOf(a, b), repo.observeAllLogs().first().map { it.habitId }.toSet())
    }

    @Test
    fun clearAchievementsRemovesEveryUnlockedBadge() = runBlocking {
        repo.addHabit(newHabit()) // unlocks FIRST_HABIT
        assertTrue(repo.observeAchievements().first().isNotEmpty())
        repo.clearAchievements()
        assertTrue(repo.observeAchievements().first().isEmpty())
    }
}
