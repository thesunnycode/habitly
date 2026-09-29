package com.habitly.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeArchivedHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}
