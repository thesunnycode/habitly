package com.habitly.app.domain

import java.time.LocalDate

object StreakCalculator {

    fun currentStreak(records: List<CompletionRecord>, today: LocalDate): Int {
        val byDate = records.associateBy { it.date }
        var streak = 0
        var day = today
        while (byDate[day]?.completed == true) {
            streak++
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
     * Completion over every day that has elapsed since tracking began, not just the days that
     * happen to have a record. A day the user never opened the app is a miss, which is what the
     * calendar already shows them — dividing by record count instead reports 100% next to
     * visible gaps.
     */
    fun completionRate(records: List<CompletionRecord>, today: LocalDate): Float {
        val relevant = records.filterNot { it.date.isAfter(today) }
        if (relevant.isEmpty()) return 0f
        val firstDay = relevant.minOf { it.date }
        val elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(firstDay, today) + 1
        if (elapsedDays <= 0) return 0f
        return relevant.count { it.completed }.toFloat() / elapsedDays
    }
}
