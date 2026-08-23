package com.habitly.app.domain

import java.time.LocalDate

/**
 * Nothing recorded, or a numeric goal still in progress: either way today has not been decided yet,
 * so it must not be allowed to break a streak while the day is still running.
 */
private fun CompletionStatus?.isUnjudged(): Boolean =
    this == null || this == CompletionStatus.PARTIAL

object StreakCalculator {

    /**
     * Consecutive days the habit was kept, counting back from [today] and skipping days the
     * [schedule] never asked for.
     *
     * An unlogged today does not break the streak — the day is not over, and zeroing the number
     * every midnight is the behaviour that makes people abandon a habit. An *explicitly* logged
     * miss today does break it, because that is the user telling us the day is lost. A day the user
     * marked as skipped is neutral — it neither extends the streak nor ends it.
     */
    fun currentStreak(
        records: List<CompletionRecord>,
        today: LocalDate,
        schedule: HabitSchedule = HabitSchedule.DAILY,
    ): Int {
        val byDate = records.associate { it.date to it.status }
        val earliest = records.minOfOrNull { it.date } ?: return 0

        var day = today
        if (schedule.isDue(today) && byDate[today].isUnjudged()) {
            // Today is still open: judge the streak as it stood yesterday.
            day = today.minusDays(1)
        }

        var streak = 0
        while (!day.isBefore(earliest)) {
            if (schedule.isDue(day)) {
                when (byDate[day]) {
                    CompletionStatus.DONE -> streak++
                    // A declared rest day is neutral: it neither extends nor ends the run.
                    CompletionStatus.SKIPPED -> Unit
                    else -> break
                }
            }
            day = day.minusDays(1)
        }
        return streak
    }

    /**
     * The most recent scheduled day that ended a streak — an explicit miss, or a due day that was
     * never logged. Null while the streak is intact. This is what lets the UI offer to turn that day
     * into a rest day instead of just reporting a zero.
     */
    fun lastBrokenDueDay(
        records: List<CompletionRecord>,
        today: LocalDate,
        schedule: HabitSchedule = HabitSchedule.DAILY,
    ): LocalDate? {
        val byDate = records.associate { it.date to it.status }
        val earliest = records.minOfOrNull { it.date } ?: return null

        var day = today
        if (schedule.isDue(today) && byDate[today].isUnjudged()) day = today.minusDays(1)

        while (!day.isBefore(earliest)) {
            if (schedule.isDue(day)) {
                when (byDate[day]) {
                    CompletionStatus.DONE, CompletionStatus.SKIPPED -> Unit
                    else -> return day
                }
            }
            day = day.minusDays(1)
        }
        return null
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
        // Declared rest days leave the rate unchanged, so they come out of the denominator.
        val skippedDueDays = relevant.count {
            it.status == CompletionStatus.SKIPPED && schedule.isDue(it.date)
        }
        val dueDays = schedule.dueDaysBetween(firstDay, today) - skippedDueDays
        if (dueDays <= 0) return 0f
        val keptDueDays = relevant.count { it.completed && schedule.isDue(it.date) }
        return (keptDueDays.toFloat() / dueDays).coerceAtMost(1f)
    }
}
