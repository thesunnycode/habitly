package com.habitly.app.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.repository.HabitRepository
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
class SeedDataProviderTest {

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

    private val today = LocalDate.of(2026, 8, 22)

    private suspend fun seed() = SeedDataProvider.seed(repo, db.habitDao(), db.habitLogDao(), today)

    private suspend fun addOwnHabit(name: String) = repo.addHabit(
        com.habitly.app.data.local.HabitEntity(
            0, name, "star", "#000000", "Custom",
            com.habitly.app.domain.FrequencyType.DAILY, null, null, null, 0L, false,
        )
    )

    @Test
    fun seedInsertsSixHabitsIntoAnEmptyDatabase() = runBlocking {
        assertEquals(6, seed())
        assertEquals(6, db.habitDao().observeAllHabits().first().size)
    }

    @Test
    fun seedBackfillsEnoughHistoryForWeekdayInsights() = runBlocking {
        seed()
        val water = db.habitDao().observeAllHabits().first().first { it.name == "Drink Water" }
        val logs = db.habitLogDao().observeLogsForHabit(water.id).first()
        // Four of every weekday need 28 days; the demo carries eight weeks.
        assertTrue(logs.size >= 40)
        assertTrue(logs.minOf { LocalDate.parse(it.date) } <= today.minusDays(28))
    }

    @Test
    fun seedWorksAlongsideTheUsersOwnHabits() = runBlocking {
        addOwnHabit("My habit")
        addOwnHabit("workout")
        // Every demo habit but the one whose name is already taken.
        assertEquals(5, seed())
        assertEquals(7, db.habitDao().observeAllHabits().first().size)
    }

    @Test
    fun seedingTwiceAddsNothingTheSecondTime() = runBlocking {
        seed()
        assertEquals(0, seed())
        assertEquals(6, db.habitDao().observeAllHabits().first().size)
    }

    @Test
    fun seedAndSilenceBadgesMarksEveryStoredBadgeSeen() = runBlocking {
        var seen = emptySet<String>()
        SeedDataProvider.seedAndSilenceBadges(repo, db.habitDao(), db.habitLogDao(), today) { seen = seen + it }
        val stored = repo.observeAchievements().first().map { it.type }.toSet()
        assertTrue(stored.isNotEmpty())
        assertEquals(stored, seen)
    }

    @Test
    fun seedAndSilenceBadgesMarksNothingWhenNothingWasSeeded() = runBlocking {
        seed()
        var called = false
        SeedDataProvider.seedAndSilenceBadges(repo, db.habitDao(), db.habitLogDao(), today) { called = true }
        assertEquals(false, called)
    }
}
