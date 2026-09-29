package com.habitly.app.domain

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Everything the badge rules read, computed once from all habits and logs. */
data class AchievementSnapshot(
    val bestStreak: Int = 0,
    val perfectDays: Int = 0,
    val longestPerfectRun: Int = 0,
    val bestMonthPercent: Int = 0,
    val comebacks: Int = 0,
    val totalCheckIns: Int = 0,
    val goalDaysMet: Int = 0,
    val habitsEver: Int = 0,
    val activeHabits: Int = 0,
    val earlyCheckIns: Int = 0,
    val restDays: Int = 0,
)

object AchievementSnapshotBuilder {
    fun build(habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AchievementSnapshot {
        if (habits.isEmpty()) return AchievementSnapshot()
        val byHabit = logs.groupBy { it.habitId }
        val statusOf = logs.associate { (it.habitId to LocalDate.parse(it.date)) to it.status }
        val schedules = habits.associate { h ->
            h.id to HabitSchedule.parse(
                type = h.frequencyType,
                frequencyDays = h.frequencyDays,
                intervalDays = h.intervalDays,
                startDate = Instant.ofEpochMilli(h.createdAt).atZone(zone).toLocalDate(),
            )
        }
        val startOf = habits.associate { it.id to Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        val kept = setOf(CompletionStatus.DONE, CompletionStatus.SKIPPED)

        val bestStreak = habits.maxOf { h ->
            StreakCalculator.bestStreak((byHabit[h.id] ?: emptyList()).map { CompletionRecord(LocalDate.parse(it.date), it.status) })
        }

        // Perfect days: every habit due that day kept, at least one actually done.
        val first = startOf.values.min()
        var perfectDays = 0; var run = 0; var longestRun = 0
        var d = first
        while (!d.isAfter(today)) {
            val due = habits.filter { !startOf.getValue(it.id).isAfter(d) && schedules.getValue(it.id).isDue(d) }
            val perfect = due.isNotEmpty() && due.all { statusOf[it.id to d] in kept } &&
                due.any { statusOf[it.id to d] == CompletionStatus.DONE }
            if (perfect) { perfectDays++; run++; longestRun = maxOf(longestRun, run) } else if (d != today) run = 0
            d = d.plusDays(1)
        }

        // Best finished calendar month, over days each habit was due.
        var bestMonth = 0
        var month = YearMonth.from(first)
        while (month.isBefore(YearMonth.from(today))) {
            var due = 0; var done = 0
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                habits.filter { !startOf.getValue(it.id).isAfter(date) && schedules.getValue(it.id).isDue(date) }.forEach {
                    due++; if (statusOf[it.id to date] in kept) done++
                }
            }
            if (due >= 10) bestMonth = maxOf(bestMonth, done * 100 / due)
            month = month.plusMonths(1)
        }

        // Comebacks: a done day after a miss that ended a run of at least 3.
        var comebacks = 0
        habits.forEach { h ->
            var streak = 0; var armed = false
            var day = startOf.getValue(h.id)
            while (day.isBefore(today)) {
                if (schedules.getValue(h.id).isDue(day)) {
                    when (statusOf[h.id to day]) {
                        CompletionStatus.DONE -> { if (armed) { comebacks++; armed = false }; streak++ }
                        CompletionStatus.SKIPPED -> Unit
                        else -> { if (streak >= 3) armed = true; streak = 0 }
                    }
                }
                day = day.plusDays(1)
            }
            if (armed && statusOf[h.id to today] == CompletionStatus.DONE) comebacks++
        }

        val done = logs.filter { it.status == CompletionStatus.DONE }
        val goalHabits = habits.filter { it.targetCount != null }.map { it.id }.toSet()
        return AchievementSnapshot(
            bestStreak = bestStreak,
            perfectDays = perfectDays,
            longestPerfectRun = longestRun,
            bestMonthPercent = bestMonth,
            comebacks = comebacks,
            totalCheckIns = done.size,
            goalDaysMet = done.count { it.habitId in goalHabits },
            habitsEver = habits.size,
            activeHabits = habits.count { !it.isArchived },
            earlyCheckIns = done.count { log -> log.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).hour < 8 } == true },
            restDays = logs.count { it.status == CompletionStatus.SKIPPED },
        )
    }
}
