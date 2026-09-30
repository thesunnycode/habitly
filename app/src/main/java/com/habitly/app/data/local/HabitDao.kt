package com.habitly.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeArchivedHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /**
     * Numbers the habits 1..n in the given order in one transaction, so observers see the new
     * order once instead of every intermediate step.
     */
    @Transaction
    suspend fun updateSortOrders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index + 1) }
    }

    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()

    /**
     * Wipes every habit (archived included), log and badge atomically. Logs cascade with their
     * habits, but are deleted explicitly too so the reset does not lean on the foreign-key pragma.
     */
    @Transaction
    suspend fun deleteEverything() {
        deleteAllLogs()
        deleteAllHabits()
        deleteAllAchievements()
    }
}
