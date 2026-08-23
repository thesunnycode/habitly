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

    @Test
    fun seedIfEmptyInsertsFourHabitsIntoAnEmptyDatabase() = runBlocking {
        SeedDataProvider.seedIfEmpty(repo, db.habitDao(), LocalDate.of(2026, 8, 22))
        val habits = db.habitDao().observeAllHabits().first()
        assertEquals(4, habits.size)
    }

    @Test
    fun seedIfEmptyBackfillsAtLeastTenDaysOfLogsPerHabit() = runBlocking {
        SeedDataProvider.seedIfEmpty(repo, db.habitDao(), LocalDate.of(2026, 8, 22))
        val habits = db.habitDao().observeAllHabits().first()
        val logs = db.habitLogDao().observeLogsForHabit(habits.first().id).first()
        assertTrue(logs.size >= 10)
    }

    @Test
    fun seedIfEmptyDoesNothingWhenHabitsAlreadyExist() = runBlocking {
        repo.addHabit(
            com.habitly.app.data.local.HabitEntity(
                0, "Existing", "star", "#000000", "Custom",
                com.habitly.app.domain.FrequencyType.DAILY, null, null, null, 0L, false,
            )
        )
        SeedDataProvider.seedIfEmpty(repo, db.habitDao(), LocalDate.of(2026, 8, 22))
        val habits = db.habitDao().observeAllHabits().first()
        assertEquals(1, habits.size)
    }
}
