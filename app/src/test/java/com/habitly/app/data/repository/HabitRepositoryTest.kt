package com.habitly.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun toggleCompletionReturnsFirstHabitAchievementForTheFirstEverHabit() = runBlocking {
        val id = repo.addHabit(newHabit())
        val today = LocalDate.of(2026, 8, 22)
        val unlocked = repo.toggleCompletion(id, today, null)
        assertTrue(AchievementType.FIRST_HABIT in unlocked)
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
}
