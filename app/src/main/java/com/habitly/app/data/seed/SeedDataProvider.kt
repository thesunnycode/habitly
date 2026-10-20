package com.habitly.app.data.seed

import com.habitly.app.data.local.HabitDao
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogDao
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.HabitRepository
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Demo habits with eight weeks of believable history: weekday habits, a numeric goal, rest days,
 * a slump and a recovery, notes. Enough for every screen to have something to show, including the
 * weekday insights (four of each weekday) and the risk forecast (40+ judged days).
 */
object SeedDataProvider {

    const val HISTORY_DAYS = 56

    private class SeedHabit(
        val name: String,
        val icon: String,
        val colorHex: String,
        val category: String,
        val reminder: String?,
        val days: Set<DayOfWeek>? = null,
        val target: Int? = null,
        val unit: String? = null,
        /** Chance of a DONE on [daysAgo] days ago, on [date]. */
        val odds: (daysAgo: Int, date: LocalDate) -> Double,
    )

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private val SEED_HABITS = listOf(
        SeedHabit("Drink Water", "water_drop", "#22B8CF", "Health", "09:00", target = 8, unit = "cups") { _, d ->
            if (d.dayOfWeek in WEEKEND) 0.6 else 0.88
        },
        SeedHabit("Read 20 Minutes", "menu_book", "#FF922B", "Learning", "21:30") { _, d ->
            // Strong all week, falls apart on Friday and Saturday nights: a weekday insight.
            if (d.dayOfWeek == DayOfWeek.FRIDAY || d.dayOfWeek == DayOfWeek.SATURDAY) 0.2 else 0.9
        },
        SeedHabit(
            "Workout", "fitness_center", "#FF6B6B", "Fitness", "07:00",
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        ) { _, _ -> 0.85 },
        SeedHabit("Meditate", "self_improvement", "#9775FA", "Wellness", "07:30") { ago, _ ->
            // A slump three to four weeks ago, then an unbroken run up to yesterday.
            when (ago) {
                in 1..12 -> 1.0
                13 -> 0.0
                in 18..30 -> 0.25
                else -> 0.8
            }
        },
        SeedHabit("Evening Walk", "walk", "#51CF66", "Fitness", "18:30") { _, d ->
            if (d.dayOfWeek in WEEKEND) 0.95 else 0.6
        },
        SeedHabit("No Phone After 10pm", "phone_off", "#748FFC", "Wellness", null) { ago, _ ->
            // Getting better: about a third early on, most nights lately.
            0.85 - 0.5 * ago / HISTORY_DAYS
        },
    )

    private val NOTES = mapOf(
        "Read 20 Minutes" to listOf("Finished a chapter of Atomic Habits", "Read on the bus"),
        "Workout" to listOf("Leg day, felt strong", "New personal best on push-ups"),
        "Meditate" to listOf("10 minutes, very calm", "Hard to focus today"),
        "Evening Walk" to listOf("Walked to the park and back"),
    )

    /**
     * Seeds the demo habits and then marks every badge they unlocked as seen, so demo history does
     * not set off a run of celebrations. Returns how many habits were added.
     */
    suspend fun seedAndSilenceBadges(
        repository: HabitRepository,
        habitDao: HabitDao,
        logDao: HabitLogDao,
        today: LocalDate,
        markSeen: suspend (Set<String>) -> Unit,
    ): Int {
        val added = seed(repository, habitDao, logDao, today)
        if (added == 0) return 0
        markSeen(repository.observeAchievements().first().map { it.type }.toSet())
        return added
    }

    /**
     * Adds each demo habit that isn't already there (matched by name), so it works alongside the
     * user's own habits and a second tap adds nothing. Returns how many habits were added.
     */
    suspend fun seed(
        repository: HabitRepository,
        habitDao: HabitDao,
        logDao: HabitLogDao,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val existing = habitDao.observeAllHabits().first().map { it.name.lowercase() }.toSet()
        val start = today.minusDays(HISTORY_DAYS.toLong())
        // Created when its history begins, so the stats don't treat that history as backfill.
        val createdAt = start.atStartOfDay(zone).toInstant().toEpochMilli()
        var added = 0

        SEED_HABITS.forEachIndexed { index, seed ->
            if (seed.name.lowercase() in existing) return@forEachIndexed
            val habitId = habitDao.insert(
                HabitEntity(
                    name = seed.name,
                    icon = seed.icon,
                    colorHex = seed.colorHex,
                    category = seed.category,
                    frequencyType = if (seed.days == null) FrequencyType.DAILY else FrequencyType.SPECIFIC_DAYS,
                    frequencyDays = seed.days?.sorted()?.joinToString(",") { it.name },
                    intervalDays = null,
                    reminderTime = seed.reminder,
                    createdAt = createdAt,
                    isArchived = false,
                    targetCount = seed.target,
                    unit = seed.unit,
                    sortOrder = (habitDao.maxSortOrder() ?: 0) + 1,
                )
            )
            // Fixed per habit, so every load of the demo looks the same.
            val random = Random(index * 31 + 7)
            val notes = NOTES[seed.name].orEmpty()
            for (ago in HISTORY_DAYS downTo 1) {
                val date = today.minusDays(ago.toLong())
                if (seed.days != null && date.dayOfWeek !in seed.days) continue
                val roll = random.nextDouble()
                val status = when {
                    roll < seed.odds(ago, date) -> CompletionStatus.DONE
                    seed.target != null && roll < 0.95 -> CompletionStatus.PARTIAL
                    // Now and then a declared rest day rather than a plain miss.
                    roll > 0.93 -> CompletionStatus.SKIPPED
                    else -> null
                } ?: continue
                logDao.upsert(
                    HabitLogEntity(
                        habitId = habitId,
                        date = date.toString(),
                        status = status,
                        completedAt = if (status == CompletionStatus.DONE) {
                            date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
                        } else null,
                        note = notes.getOrNull(ago % 9).takeIf { status == CompletionStatus.DONE },
                        progress = when (status) {
                            CompletionStatus.DONE -> seed.target ?: 0
                            CompletionStatus.PARTIAL -> 3 + random.nextInt(4)
                            else -> 0
                        },
                    )
                )
            }
            added++
        }

        // Today is left open so the demo can show checking things off, except a head start on water.
        habitDao.observeAllHabits().first().firstOrNull { it.name == "Drink Water" && it.name.lowercase() !in existing }
            ?.let { water ->
                logDao.upsert(HabitLogEntity(habitId = water.id, date = today.toString(), status = CompletionStatus.PARTIAL, completedAt = null, note = null, progress = 3))
            }

        if (added > 0) repository.reconcileAchievements()
        return added
    }
}
