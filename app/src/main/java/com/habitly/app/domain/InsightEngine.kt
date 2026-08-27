package com.habitly.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Everything the engine needs to know about one habit. Deliberately not a data-layer type. */
data class HabitHistory(
    val name: String,
    val records: List<CompletionRecord>,
    val currentStreak: Int,
    val bestStreak: Int,
    val schedule: HabitSchedule = HabitSchedule.DAILY,
)

/**
 * Ordered by how much a reader wants to hear it. A personal best is worth interrupting for; the
 * suggestion to take a rest day is worth saying only when nothing more urgent is true.
 */
enum class InsightKind {
    PERSONAL_BEST,
    RECOVERY,
    HABIT_SLIPPING,
    WEAK_WEEKDAY,
    STRONG_WEEKDAY,
    REST_POSITIVE,
}

data class Insight(val kind: InsightKind, val text: String)

/**
 * Turns history into a few plain sentences.
 *
 * The design rule, taken from how Oura and Gentler Streak write theirs: one fact plus one
 * implication, specific enough to be checkable, and never scolding. The engineering rule matters
 * just as much — every template carries a minimum sample size, because an insight drawn from three
 * days of data is a guess wearing a fact's clothing. When nothing qualifies, the engine returns
 * nothing rather than padding the screen.
 */
object InsightEngine {

    private const val MIN_DAYS_OF_HISTORY = 3
    private const val MIN_STREAK_FOR_BEST = 3
    private const val MIN_STREAK_FOR_RECOVERY = 3
    private const val MIN_WEEKDAY_OCCURRENCES = 4
    private const val WEEKDAY_DEVIATION = 0.20f
    private const val SLIP_WINDOW_DAYS = 14L
    private const val SLIP_DROP = 0.25f
    private const val MIN_DUE_DAYS_PER_WINDOW = 4
    private const val REST_POSITIVE_STREAK = 14

    fun insights(
        histories: List<HabitHistory>,
        today: LocalDate,
        limit: Int = 3,
    ): List<Insight> {
        val withHistory = histories.filter { it.records.isNotEmpty() }
        if (withHistory.isEmpty()) return emptyList()

        val earliest = withHistory.minOf { h -> h.records.minOf { it.date } }
        if (ChronoUnit.DAYS.between(earliest, today) + 1 < MIN_DAYS_OF_HISTORY) return emptyList()

        val found = buildList {
            withHistory.forEach { habit ->
                personalBest(habit, today)?.let { add(it) }
                recovery(habit, today)?.let { add(it) }
                slipping(habit, today)?.let { add(it) }
                restPositive(habit)?.let { add(it) }
            }
            weekdayInsight(withHistory, today)?.let { add(it) }
        }

        // One of each kind, best kind first, so three insights never say the same thing three ways.
        return found
            .distinctBy { it.kind }
            .sortedBy { it.kind.ordinal }
            .take(limit)
    }

    private fun personalBest(habit: HabitHistory, today: LocalDate): Insight? {
        if (habit.currentStreak < MIN_STREAK_FOR_BEST) return null
        val runStart = today.minusDays((habit.currentStreak - 1).toLong())
        val before = habit.records.filter { it.date.isBefore(runStart) }
        val previousBest = StreakCalculator.bestStreak(before)
        if (habit.currentStreak <= previousBest) return null
        return Insight(
            InsightKind.PERSONAL_BEST,
            "Longest run of ${habit.name} yet: ${habit.currentStreak} days. " +
                "Your previous best was $previousBest.",
        )
    }

    private fun recovery(habit: HabitHistory, today: LocalDate): Insight? {
        if (habit.currentStreak < MIN_STREAK_FOR_RECOVERY) return null
        val runStart = today.minusDays((habit.currentStreak - 1).toLong())
        val broken = StreakCalculator.lastBrokenDueDay(
            habit.records.filter { it.date.isBefore(runStart) },
            runStart.minusDays(1),
            habit.schedule,
        ) ?: return null
        // Only interesting while it is still recent enough to remember.
        if (ChronoUnit.DAYS.between(broken, today) > 14) return null
        return Insight(
            InsightKind.RECOVERY,
            "You missed ${broken.dayOfWeek.display()} and got straight back on it — " +
                "${habit.currentStreak} days since. That is the part that matters.",
        )
    }

    private fun slipping(habit: HabitHistory, today: LocalDate): Insight? {
        val recentStart = today.minusDays(SLIP_WINDOW_DAYS - 1)
        val olderStart = recentStart.minusDays(SLIP_WINDOW_DAYS)

        val recent = rateOver(habit, recentStart, today) ?: return null
        val older = rateOver(habit, olderStart, recentStart.minusDays(1)) ?: return null
        if (older - recent < SLIP_DROP) return null

        return Insight(
            InsightKind.HABIT_SLIPPING,
            "${habit.name} has slipped: ${(recent * 100).toInt()}% over the last two weeks, " +
                "down from ${(older * 100).toInt()}%.",
        )
    }

    /** Completion rate over a window, or null when the window holds too few due days to judge. */
    private fun rateOver(habit: HabitHistory, from: LocalDate, to: LocalDate): Float? {
        val dueDays = habit.schedule.dueDaysBetween(from, to)
        if (dueDays < MIN_DUE_DAYS_PER_WINDOW) return null
        val done = habit.records.count {
            it.status == CompletionStatus.DONE && !it.date.isBefore(from) && !it.date.isAfter(to)
        }
        return (done.toFloat() / dueDays).coerceAtMost(1f)
    }

    private fun restPositive(habit: HabitHistory): Insight? {
        if (habit.currentStreak < REST_POSITIVE_STREAK) return null
        return Insight(
            InsightKind.REST_POSITIVE,
            "${habit.currentStreak} days of ${habit.name} without a miss. " +
                "A planned rest day will not break this.",
        )
    }

    /**
     * The most useful pattern in the data: which weekday keeps getting dropped. Reported only when
     * that weekday has come round often enough for the pattern to be real rather than coincidence.
     */
    private fun weekdayInsight(histories: List<HabitHistory>, today: LocalDate): Insight? {
        val done = mutableMapOf<DayOfWeek, Int>()
        val due = mutableMapOf<DayOfWeek, Int>()

        histories.forEach { habit ->
            val first = habit.records.minOf { it.date }
            var day = first
            while (!day.isAfter(today)) {
                if (habit.schedule.isDue(day)) {
                    due[day.dayOfWeek] = (due[day.dayOfWeek] ?: 0) + 1
                }
                day = day.plusDays(1)
            }
            habit.records.filter { it.status == CompletionStatus.DONE }.forEach {
                done[it.dayOfWeek()] = (done[it.dayOfWeek()] ?: 0) + 1
            }
        }

        val rates = DayOfWeek.entries.mapNotNull { weekday ->
            val occurrences = due[weekday] ?: 0
            if (occurrences < MIN_WEEKDAY_OCCURRENCES) {
                null
            } else {
                weekday to (done[weekday] ?: 0).toFloat() / occurrences
            }
        }
        if (rates.size < 2) return null

        val mean = rates.map { it.second }.average().toFloat()
        val worst = rates.minBy { it.second }
        val best = rates.maxBy { it.second }

        return when {
            mean - worst.second >= WEEKDAY_DEVIATION -> Insight(
                InsightKind.WEAK_WEEKDAY,
                "${worst.first.display()}s are your weak spot — " +
                    "${(worst.second * 100).toInt()}% kept, against " +
                    "${(mean * 100).toInt()}% on an average day.",
            )
            best.second - mean >= WEEKDAY_DEVIATION -> Insight(
                InsightKind.STRONG_WEEKDAY,
                "${best.first.display()}s are your best day: ${(best.second * 100).toInt()}% kept.",
            )
            else -> null
        }
    }

    private fun CompletionRecord.dayOfWeek() = date.dayOfWeek

    private fun DayOfWeek.display() = getDisplayName(TextStyle.FULL, Locale.getDefault())
}
