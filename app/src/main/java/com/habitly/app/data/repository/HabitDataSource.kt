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

    /**
     * Adds one unit towards a numeric goal, marking the day done once the target is reached and
     * wrapping back to nothing recorded once it is exceeded.
     */
    suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType>

    /** Records an explicit status for a day, or clears the day entirely when [status] is null. */
    suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String? = null,
    ): Set<AchievementType>
    fun observeArchivedHabits(): Flow<List<HabitEntity>>
    suspend fun setArchived(habitId: Long, archived: Boolean)
    suspend fun reorder(orderedIds: List<Long>)
    suspend fun setReminder(habitId: Long, time: String?)
    suspend fun getHabit(habitId: Long): HabitEntity?
    fun observeAchievements(): Flow<List<AchievementEntity>>
    fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>>
}
