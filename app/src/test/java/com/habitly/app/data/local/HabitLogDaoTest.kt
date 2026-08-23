package com.habitly.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitLogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var logDao: HabitLogDao
    private var habitId: Long = 0

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        habitDao = db.habitDao()
        logDao = db.habitLogDao()
        habitId = habitDao.insert(
            HabitEntity(
                id = 0, name = "Read", icon = "book", colorHex = "#6B9080", category = "Learning",
                frequencyType = FrequencyType.DAILY, frequencyDays = null,
                intervalDays = null, reminderTime = null, createdAt = 0L, isArchived = false,
            )
        )
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun upsertThenGetLogReturnsTheLogForThatDate() = runBlocking {
        logDao.upsert(HabitLogEntity(0, habitId, "2026-08-20", CompletionStatus.DONE, 123L, null))
        val log = logDao.getLog(habitId, "2026-08-20")
        assertNotNull(log)
        assertEquals(CompletionStatus.DONE, log!!.status)
    }

    @Test
    fun getLogReturnsNullWhenNoLogExistsForThatDate() = runBlocking {
        assertNull(logDao.getLog(habitId, "2026-08-21"))
    }

    @Test
    fun upsertWithSameHabitIdAndDateReplacesThePreviousLog() = runBlocking {
        logDao.upsert(HabitLogEntity(0, habitId, "2026-08-20", CompletionStatus.MISSED, null, null))
        logDao.upsert(HabitLogEntity(0, habitId, "2026-08-20", CompletionStatus.DONE, 456L, "felt great"))
        val logs = logDao.observeLogsForHabit(habitId).first()
        assertEquals(1, logs.size)
        assertEquals("felt great", logs.first().note)
    }

    @Test
    fun deletingTheParentHabitCascadesToItsLogs() = runBlocking {
        logDao.upsert(HabitLogEntity(0, habitId, "2026-08-20", CompletionStatus.DONE, 1L, null))
        habitDao.delete(habitDao.getById(habitId)!!)
        assertEquals(0, logDao.observeLogsForHabit(habitId).first().size)
    }
}
