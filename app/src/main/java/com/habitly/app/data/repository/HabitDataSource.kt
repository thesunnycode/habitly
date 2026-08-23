package com.habitly.app.data.repository

import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitDataSource {
    fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>>
    suspend fun addHabit(habit: HabitEntity): Long
    suspend fun updateHabit(habit: HabitEntity)
    suspend fun deleteHabit(habit: HabitEntity)
    suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType>
    fun observeAchievements(): Flow<List<AchievementEntity>>
    fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>>
}
