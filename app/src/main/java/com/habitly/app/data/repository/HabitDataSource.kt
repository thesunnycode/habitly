package com.habitly.app.data.repository

import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitDataSource {
    /** Active habits only, with their numbers. */
    fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>>

    /** One habit with its numbers, archived or not; null once it no longer exists. */
    fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?>
    suspend fun addHabit(habit: HabitEntity): Long
    suspend fun updateHabit(habit: HabitEntity)
    suspend fun deleteHabit(habit: HabitEntity)
    /** Ticks or un-ticks a day; [note] follows [setStatus]'s rules, so null keeps the day's note. */
    suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType>

    /**
     * Adds one unit towards a numeric goal, marking the day done once the target is reached and
     * wrapping back to nothing recorded once it is exceeded.
     */
    suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType>

    /**
     * Records an explicit status for a day, or clears the day entirely when [status] is null.
     *
     * [note] edits the day's note: null keeps whatever note is already there, a blank string
     * removes it, and anything else replaces it (trimmed).
     */
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

    /** Every badge with its unlock time (null while locked) and progress towards its target. */
    fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>>

    /** Stores every badge already earned but not yet unlocked, returning the ones it stored. */
    suspend fun reconcileAchievements(): Set<AchievementType>
    /** Deletes every habit (archived too), every log and every unlocked badge, atomically. */
    suspend fun resetAll()
    fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>>

    /** Every habit, archived ones included. */
    fun observeAllHabits(): Flow<List<HabitEntity>>

    /** Every log of every habit. */
    fun observeAllLogs(): Flow<List<HabitLogEntity>>
}

data class AchievementProgress(val type: AchievementType, val unlockedAt: Long?, val progress: Int)
