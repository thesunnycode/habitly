package com.habitly.app.data.repository

import com.habitly.app.data.local.AchievementDao
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitDao
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogDao
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.AchievementEvaluator
import com.habitly.app.domain.AchievementSnapshotBuilder
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.ContributionLevels
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.StreakCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

/** How far back each habit's grid levels reach: a full year, covering the dense Today card, Detail and a year view. */
private const val LEVEL_WEEKS = 53L

class HabitRepository(
    private val habitDao: HabitDao,
    private val logDao: HabitLogDao,
    private val achievementDao: AchievementDao,
    /** Where the stats maths runs; tests may pass their own dispatcher. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HabitDataSource {

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> =
        combine(habitDao.observeActiveHabits(), logDao.observeAllLogs()) { habits, allLogs ->
            val logsByHabit = allLogs.groupBy { it.habitId }
            habits.map { habit -> buildStats(habit, logsByHabit[habit.id] ?: emptyList(), today) }
        }
            // Parsing every log and walking streaks is too much for the main thread on each change.
            .flowOn(computeDispatcher)
            .distinctUntilChanged()

    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> =
        combine(habitDao.observeById(habitId), logDao.observeLogsForHabit(habitId)) { habit, logs ->
            habit?.let { buildStats(it, logs, today) }
        }
            .flowOn(computeDispatcher)
            .distinctUntilChanged()

    /** One habit's numbers from its own logs; archived or not makes no difference here. */
    private fun buildStats(habit: HabitEntity, logs: List<HabitLogEntity>, today: LocalDate): HabitWithStats {
        val records = logs.map { CompletionRecord(LocalDate.parse(it.date, ISO), it.status) }
        val todayLog = logs.find { it.date == today.format(ISO) }
        val completedByDate = records.filter { it.completed }.map { it.date }.toSet()
        val statusByDate = records.associate { it.date to it.status }
        val last7Days = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) in completedByDate }
        val schedule = HabitSchedule.parse(
            type = habit.frequencyType,
            frequencyDays = habit.frequencyDays,
            intervalDays = habit.intervalDays,
            startDate = Instant.ofEpochMilli(habit.createdAt)
                .atZone(ZoneId.systemDefault()).toLocalDate(),
        )
        return HabitWithStats(
            habit = habit,
            currentStreak = StreakCalculator.currentStreak(records, today, schedule),
            bestStreak = StreakCalculator.bestStreak(records),
            completionRate = StreakCalculator.completionRate(records, today, schedule),
            isCompletedToday = todayLog?.status == CompletionStatus.DONE,
            todayStatus = todayLog?.status,
            last7Days = last7Days,
            isDueToday = schedule.isDue(today),
            last7DaysDue = (6 downTo 0).map { offset -> schedule.isDue(today.minusDays(offset.toLong())) },
            last7DaysStatus = (6 downTo 0).map { offset -> statusByDate[today.minusDays(offset.toLong())] },
            lastBrokenDueDay = StreakCalculator.lastBrokenDueDay(records, today, schedule),
            todayProgress = todayLog?.progress ?: 0,
            schedule = schedule,
            levels = ContributionLevels.forHabit(
                records.filter { it.date >= today.minusWeeks(LEVEL_WEEKS) },
            ),
            todayNote = todayLog?.note,
            notes = logs.mapNotNull { log ->
                log.note?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(log.date, ISO) to it }
            }.toMap(),
        )
    }

    override suspend fun addHabit(habit: HabitEntity): Long {
        val order = if (habit.sortOrder != 0) habit.sortOrder else (habitDao.maxSortOrder() ?: 0) + 1
        val id = habitDao.insert(habit.copy(sortOrder = order))
        evaluateAchievements()
        return id
    }

    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = habitDao.observeArchivedHabits()

    override suspend fun setArchived(habitId: Long, archived: Boolean) = habitDao.setArchived(habitId, archived)

    override suspend fun reorder(orderedIds: List<Long>) = habitDao.updateSortOrders(orderedIds)

    override suspend fun setReminder(habitId: Long, time: String?) {
        val habit = habitDao.getById(habitId) ?: return
        habitDao.update(habit.copy(reminderTime = time))
    }

    override suspend fun getHabit(habitId: Long): HabitEntity? = habitDao.getById(habitId)

    override suspend fun updateHabit(habit: HabitEntity) = habitDao.update(habit)

    override suspend fun deleteHabit(habit: HabitEntity) = habitDao.delete(habit)

    override fun observeAchievements(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> =
        combine(habitDao.observeAllHabits(), logDao.observeAllLogs(), achievementDao.observeAll()) { habits, logs, unlocked ->
            val snapshot = AchievementSnapshotBuilder.build(habits, logs, today)
            val unlockedAt = unlocked.mapNotNull { entity ->
                runCatching { AchievementType.valueOf(entity.type) }.getOrNull()?.let { it to entity.unlockedAt }
            }.toMap()
            AchievementType.entries.map { type ->
                AchievementProgress(type, unlockedAt[type], AchievementEvaluator.progress(type, snapshot))
            }
        }.flowOn(Dispatchers.Default) // The snapshot walks every day since the first habit; keep it off the main thread.

    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> =
        logDao.observeLogsForHabit(habitId).map { logs ->
            logs.map { CompletionRecord(LocalDate.parse(it.date, ISO), it.status) }
        }

    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> {
        val existing = logDao.getLog(habitId, date.format(ISO))
        // Un-ticking clears the day rather than recording a miss — see setStatus.
        val target = if (existing?.status == CompletionStatus.DONE) null else CompletionStatus.DONE
        return setStatus(habitId, date, target, note)
    }

    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> {
        val habit = habitDao.getById(habitId) ?: return emptySet()
        val target = habit.targetCount
            // No goal to count towards, so a tap is just a tick.
            ?: return toggleCompletion(habitId, date, null)

        val dateStr = date.format(ISO)
        val existing = logDao.getLog(habitId, dateStr)
        val current = existing?.progress ?: 0

        // Tapping past a met goal clears the day, so the control stays a way out as well as in.
        if (current >= target) {
            logDao.deleteLog(habitId, dateStr)
            return emptySet()
        }

        val next = current + 1
        val reached = next >= target
        logDao.upsert(
            HabitLogEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                date = dateStr,
                status = if (reached) CompletionStatus.DONE else CompletionStatus.PARTIAL,
                completedAt = if (reached) System.currentTimeMillis() else null,
                note = existing?.note,
                progress = next,
            )
        )
        return if (reached) evaluateAchievements() else emptySet()
    }

    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        val dateStr = date.format(ISO)
        val existing = logDao.getLog(habitId, dateStr)

        if (status == null) {
            logDao.deleteLog(habitId, dateStr)
            return emptySet()
        }

        logDao.upsert(
            HabitLogEntity(
                id = existing?.id ?: 0,
                habitId = habitId,
                date = dateStr,
                status = status,
                completedAt = if (status == CompletionStatus.DONE) System.currentTimeMillis() else null,
                // Null leaves the note alone; blank is an explicit "remove it".
                note = if (note == null) existing?.note else note.trim().takeIf { it.isNotEmpty() },
                progress = when (status) {
                    // Marking a goal habit done fills its counter; anything else empties it.
                    CompletionStatus.DONE -> habitDao.getById(habitId)?.targetCount ?: 0
                    else -> 0
                },
            )
        )

        // A rest day can earn Second chance, so it is evaluated as well as a completion.
        if (status != CompletionStatus.DONE && status != CompletionStatus.SKIPPED) return emptySet()
        return evaluateAchievements()
    }

    override suspend fun reconcileAchievements(): Set<AchievementType> = evaluateAchievements()

    /** Habits (archived too), their logs and every badge, in one transaction so it never half-completes. */
    override suspend fun resetAll() = achievementLock.withLock { habitDao.deleteEverything() }

    override fun observeAllHabits(): Flow<List<HabitEntity>> = habitDao.observeAllHabits()

    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = logDao.observeAllLogs()

    /** Unlocks every badge the current habits and logs have earned, returning the newly unlocked ones. */
    private suspend fun evaluateAchievements(): Set<AchievementType> = achievementLock.withLock {
        // Read, decide and insert under one lock, so two check-ins racing cannot both store a badge.
        val habits = habitDao.observeAllHabits().first()
        val logs = logDao.observeAllLogs().first()
        val snapshot = withContext(Dispatchers.Default) { AchievementSnapshotBuilder.build(habits, logs, LocalDate.now()) }
        val alreadyUnlocked = achievementDao.observeAll().first()
            .mapNotNull { runCatching { AchievementType.valueOf(it.type) }.getOrNull() }
            .toSet()

        val newlyUnlocked = AchievementEvaluator.evaluate(snapshot, alreadyUnlocked)
        val now = System.currentTimeMillis()
        newlyUnlocked.forEach { type ->
            achievementDao.insert(AchievementEntity(0, null, type.name, now))
        }
        newlyUnlocked
    }

    companion object {
        /** Process-wide: screens, workers and the celebration host each build their own repository. */
        private val achievementLock = Mutex()
    }
}
