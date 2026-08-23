package com.habitly.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HabitDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.habitDao()
    }

    @After
    fun teardown() { db.close() }

    private fun sampleHabit(name: String = "Drink Water") = HabitEntity(
        id = 0, name = name, icon = "water_drop", colorHex = "#6B9080", category = "Health",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null,
        reminderTime = "08:00", createdAt = 1000L, isArchived = false,
    )

    @Test
    fun insertThenObserveAllHabitsReturnsTheInsertedHabit() = runBlocking {
        dao.insert(sampleHabit())
        val habits = dao.observeActiveHabits().first()
        assertEquals(1, habits.size)
        assertEquals("Drink Water", habits.first().name)
    }

    @Test
    fun archivedHabitsAreExcludedFromObserveActiveHabits() = runBlocking {
        val id = dao.insert(sampleHabit())
        dao.setArchived(id, true)
        val habits = dao.observeActiveHabits().first()
        assertEquals(0, habits.size)
    }

    @Test
    fun getByIdReturnsNullForUnknownId() = runBlocking {
        assertNull(dao.getById(999L))
    }

    @Test
    fun deleteRemovesTheHabit() = runBlocking {
        val id = dao.insert(sampleHabit())
        dao.delete(dao.getById(id)!!)
        assertEquals(0, dao.observeActiveHabits().first().size)
    }
}
