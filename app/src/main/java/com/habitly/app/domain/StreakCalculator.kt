package com.habitly.app.domain

import java.time.LocalDate

object StreakCalculator {

    /**
     * Consecutive days the habit was kept, counting back from [today] and skipping days the
     * [schedule] never asked for.
     *
     * An unlogged today does not break the streak — the day is not over, and zeroing the number
     * every midnight is the behaviour that makes people abandon a habit. An *explicitly* logged
     * miss today does break it, because that is the user telling us the day is lost.
     */
    fun currentStreak(
        records: List<CompletionRecord>,
        today: LocalDate,
        schedule: HabitSchedule = HabitSchedule.DAILY,
    ): Int {
        val byDate = records.associate { it.date to it.completed }
        val earliest = records.minOfOrNull { it.date } ?: return 0

        var day = today
        if (schedule.isDue(today) && byDate[today] == null) {
            // Today is still open: judge the streak as it stood yesterday.
            day = today.minusDays(1)
        }

        var streak = 0
        while (!day.isBefore(earliest)) {
            if (schedule.isDue(day)) {
                if (byDate[day] == true) streak++ else break
            }
            day = day.minusDays(1)
        }
        return streak
    }

    fun bestStreak(records: List<CompletionRecord>): Int {
        val sorted = records.sortedBy { it.date }
        var best = 0
        var running = 0
        var previousDate: LocalDate? = null
        for (record in sorted) {
            val isConsecutive = previousDate != null && record.date == previousDate!!.plusDays(1)
            running = when {
                !record.completed -> 0
                isConsecutive -> running + 1
                else -> 1
            }
            best = maxOf(best, running)
            previousDate = record.date
        }
        return best
    }

    fun completionRate(records: List<CompletionRecord>): Float {
        if (records.isEmpty()) return 0f
        return records.count { it.completed }.toFloat() / records.size
    }

    /**
     * Completion measured over the days the habit was actually due, from the first record up to
     * [today]. A day the user never opened the app still counts as a miss — that is what the
     * calendar already shows them — but a day the habit was never scheduled for does not, and a
     * bonus completion on an unscheduled day cannot push the result past 100%.
     */
    fun completionRate(
        records: List<CompletionRecord>,
        today: LocalDate,
        schedule: HabitSchedule = HabitSchedule.DAILY,
    ): Float {
        val relevant = records.filterNot { it.date.isAfter(today) }
        if (relevant.isEmpty()) return 0f
        val firstDay = relevant.minOf { it.date }
        val dueDays = schedule.dueDaysBetween(firstDay, today)
        if (dueDays <= 0) return 0f
        val keptDueDays = relevant.count { it.completed && schedule.isDue(it.date) }
        return (keptDueDays.toFloat() / dueDays).coerceAtMost(1f)
    }
}
