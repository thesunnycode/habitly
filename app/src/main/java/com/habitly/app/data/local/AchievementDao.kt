package com.habitly.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Insert
    suspend fun insert(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("DELETE FROM achievements")
    suspend fun deleteAll()
}
