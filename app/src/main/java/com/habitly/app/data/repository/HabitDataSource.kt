package com.habitly.app.data.repository

import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitDataSource {
    fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>>
    suspend fun addHabit(habit: HabitEntity): Long
    suspend fun updateHabit(habit: HabitEntity)
    suspend fun deleteHabit(habit: HabitEntity)
    suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType>

    /** Records an explicit status for a day, or clears the day entirely when [status] is null. */
    suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String? = null,
    ): Set<AchievementType>
    fun observeAchievements(): Flow<List<AchievementEntity>>
    fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>>
}
