package com.habitly.app.data.repository

import com.habitly.app.data.local.AchievementDao
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitDao
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogDao
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.AchievementEvaluator
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

class HabitRepository(
    private val habitDao: HabitDao,
    private val logDao: HabitLogDao,
    private val achievementDao: AchievementDao,
) : HabitDataSource {

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> =
        combine(habitDao.observeActiveHabits(), logDao.observeAllLogs()) { habits, allLogs ->
            val logsByHabit = allLogs.groupBy { it.habitId }
            habits.map { habit ->
                val records = (logsByHabit[habit.id] ?: emptyList()).map {
                    CompletionRecord(LocalDate.parse(it.date, ISO), it.completed)
                }
                val todayLog = logsByHabit[habit.id]?.find { it.date == today.format(ISO) }
                val completedByDate = records.filter { it.completed }.map { it.date }.toSet()
                val last7Days = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) in completedByDate }
                HabitWithStats(
                    habit = habit,
                    currentStreak = StreakCalculator.currentStreak(records, today),
                    bestStreak = StreakCalculator.bestStreak(records),
                    completionRate = StreakCalculator.completionRate(records),
                    isCompletedToday = todayLog?.completed == true,
                    last7Days = last7Days,
                )
            }
        }

    override suspend fun addHabit(habit: HabitEntity): Long = habitDao.insert(habit)

    override suspend fun updateHabit(habit: HabitEntity) = habitDao.update(habit)

    override suspend fun deleteHabit(habit: HabitEntity) = habitDao.delete(habit)

    override fun observeAchievements(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> =
        logDao.observeLogsForHabit(habitId).map { logs ->
            logs.map { CompletionRecord(LocalDate.parse(it.date, ISO), it.completed) }
        }

    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        val dateStr = date.format(ISO)
        val existing = logDao.getLog(habitId, dateStr)
        val nowCompleted = existing?.completed != true
        logDao.upsert(
            HabitLogEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                date = dateStr,
                completed = nowCompleted,
                completedAt = if (nowCompleted) System.currentTimeMillis() else null,
                note = note,
            )
        )

        if (!nowCompleted) return emptySet()

        val allHabitsBefore = habitDao.observeAllHabits().first()
        val isFirstHabitEver = allHabitsBefore.size <= 1 &&
            logDao.observeLogsForHabit(habitId).first().count { it.completed } == 1

        val records = logDao.observeLogsForHabit(habitId).first().map {
            CompletionRecord(LocalDate.parse(it.date, ISO), it.completed)
        }
        val bestStreak = StreakCalculator.bestStreak(records)
        val alreadyUnlocked = achievementDao.observeAll().first()
            .mapNotNull { runCatching { AchievementType.valueOf(it.type) }.getOrNull() }
            .toSet()

        val newlyUnlocked = AchievementEvaluator.evaluate(bestStreak, isFirstHabitEver, alreadyUnlocked)
        newlyUnlocked.forEach { type ->
            achievementDao.insert(AchievementEntity(0, habitId, type.name, System.currentTimeMillis()))
        }
        return newlyUnlocked
    }
}
