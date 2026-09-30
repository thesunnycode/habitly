package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ln

data class HabitHistory(
    val habit: HabitEntity,
    val start: LocalDate,
    val schedule: HabitSchedule,
    val statuses: Map<LocalDate, CompletionStatus>,
)

object FeatureExtractor {
    private const val WALK_BACK_DAYS = 400L
    private const val LONG_DAYS = 90
    private const val LONG_PRIOR = 2.0
    private const val SAME_WEEKDAY_WEEKS = 20L
    private const val SAME_WEEKDAY_PRIOR = 4.0
    private const val MOMENTUM_DAYS = 3L

    fun histories(habits: List<HabitEntity>, logs: List<HabitLogEntity>, zone: ZoneId = ZoneId.systemDefault()): List<HabitHistory> {
        val byHabit = logs.groupBy { it.habitId }
        return habits.map { habit ->
            val statuses = byHabit[habit.id].orEmpty().associate { LocalDate.parse(it.date) to it.status }
            val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
            // Days before this were never the habit's; a backfilled log pulls the start earlier.
            val start = (statuses.keys + created).min()
            // INTERVAL habits stay anchored on the creation day, never on `start`, matching
            // HabitRepository and AiContextBuilder, so their due days agree with the rest of the app.
            val schedule = HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, created)
            HabitHistory(habit, start, schedule, statuses)
        }
    }

    fun rows(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        lookbackDays: Int = 180,
    ): List<TrainingRow> {
        val histories = histories(habits, logs, zone)
        val totals = DayTotals.of(histories, today)
        return histories.flatMap { h ->
            val from = maxOf(h.start, today.minusDays(lookbackDays.toLong()))
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { it.isBefore(today) }
                .filter { h.schedule.isDue(it) && h.statuses[it] != CompletionStatus.SKIPPED }
                .map { d -> TrainingRow(h.habit.id, d, features(h, d, totals), if (h.statuses[d] == CompletionStatus.DONE) 0 else 1) }
                .toList()
        }
    }

    /** Uses only data strictly before [date]; [totals] supplies the other habits' results for the days before it. */
    fun features(h: HabitHistory, date: LocalDate, totals: DayTotals = DayTotals.EMPTY): DoubleArray {
        val previous = ArrayList<Boolean>(LONG_DAYS) // most recent first; true = kept
        var restedPrev = false
        var sawDue = false
        // Walk back until 90 judged due days are found or the habit starts; the cap bounds sparse schedules.
        val stop = maxOf(h.start, date.minusDays(WALK_BACK_DAYS))
        var d = date.minusDays(1)
        while (!d.isBefore(stop) && previous.size < LONG_DAYS) {
            if (h.schedule.isDue(d)) {
                val status = h.statuses[d]
                if (!sawDue) { sawDue = true; restedPrev = status == CompletionStatus.SKIPPED }
                if (status != CompletionStatus.SKIPPED) previous += status == CompletionStatus.DONE
            }
            d = d.minusDays(1)
        }
        fun rate(values: List<Boolean>) = if (values.isEmpty()) 0.5 else values.count { it }.toDouble() / values.size
        // Long-run rate, shrunk towards 0.5 by a 2-day prior so a short history can't claim 0 % or 100 %.
        val rate90 = (previous.count { it } + LONG_PRIOR * 0.5) / (previous.size + LONG_PRIOR)
        val sameWeekday = (1L..SAME_WEEKDAY_WEEKS).map { date.minusWeeks(it) }
            .filter { h.isJudged(it) }
            .map { h.statuses[it] == CompletionStatus.DONE }
        val streak = previous.takeWhile { it }.size
        val rate7 = rate(previous.take(7))
        val rate28 = rate(previous.take(28))
        return DoubleArray(FEATURE_COUNT).also { x ->
            x[date.dayOfWeek.value - 1] = 1.0
            x[F_RATE7] = rate7
            x[F_RATE28] = rate28
            // This habit on this weekday, shrunk towards its own long-run rate (few samples per weekday).
            x[F_SAME_WEEKDAY] = (sameWeekday.count { it } + SAME_WEEKDAY_PRIOR * rate90) / (sameWeekday.size + SAME_WEEKDAY_PRIOR)
            x[F_STREAK] = ln(1.0 + streak)
            x[F_MISSED_PREV] = if (previous.isNotEmpty() && !previous[0]) 1.0 else 0.0
            x[F_AGE] = ln(1.0 + ChronoUnit.DAYS.between(h.start, date))
            x[F_REMINDER] = if (h.habit.reminderTime != null) 1.0 else 0.0
            x[F_WEEKEND] = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) 1.0 else 0.0
            x[F_MOMENTUM] = momentum(h, date, totals)
            x[F_SINCE_KEPT] = ln(1.0 + scheduledDaysSinceKept(h, date))
            x[F_TREND] = rate7 - rate28
            x[F_RATE90] = rate90
            x[F_RESTED_PREV] = if (restedPrev) 1.0 else 0.0
        }
    }

    /**
     * Share of the user's other habits kept over the [MOMENTUM_DAYS] days before [date] (a rough few
     * days tends to hit every habit); 0.5 when none of them was judged in that window.
     */
    private fun momentum(h: HabitHistory, date: LocalDate, totals: DayTotals): Double {
        var due = 0
        var done = 0
        for (k in 1L..MOMENTUM_DAYS) {
            val day = date.minusDays(k)
            val ownDue = if (h.isJudged(day)) 1 else 0
            val ownDone = if (ownDue == 1 && h.statuses[day] == CompletionStatus.DONE) 1 else 0
            due += (totals.due[day] ?: 0) - ownDue
            done += (totals.done[day] ?: 0) - ownDone
        }
        return if (due <= 0) 0.5 else done.coerceIn(0, due).toDouble() / due
    }

    /**
     * Scheduled (due, not rested) days after the most recent DONE and before [date], capped at
     * [MAX_SINCE_KEPT]; for a habit never kept, its scheduled days since the start.
     */
    private fun scheduledDaysSinceKept(h: HabitHistory, date: LocalDate): Int {
        val stop = maxOf(h.start, date.minusDays(WALK_BACK_DAYS))
        var count = 0
        var d = date.minusDays(1)
        while (!d.isBefore(stop) && count < MAX_SINCE_KEPT) {
            if (h.statuses[d] == CompletionStatus.DONE) break
            if (h.isJudged(d)) count++
            d = d.minusDays(1)
        }
        return count
    }

    private const val MAX_SINCE_KEPT = 30
}

/** Due on [day], on/after the habit's start, and not a rest day. */
internal fun HabitHistory.isJudged(day: LocalDate): Boolean =
    !day.isBefore(start) && schedule.isDue(day) && statuses[day] != CompletionStatus.SKIPPED

/** Per day, across all habits: how many were judged (due, not rested) and how many of those were DONE. */
class DayTotals(val done: Map<LocalDate, Int>, val due: Map<LocalDate, Int>) {
    companion object {
        val EMPTY = DayTotals(emptyMap(), emptyMap())

        /** Counts every history from its start to its latest log or [today], whichever is later. */
        fun of(histories: List<HabitHistory>, today: LocalDate): DayTotals {
            val done = HashMap<LocalDate, Int>()
            val due = HashMap<LocalDate, Int>()
            for (h in histories) {
                val end = (h.statuses.keys + today).max()
                var d = h.start
                while (!d.isAfter(end)) {
                    if (h.isJudged(d)) {
                        due.merge(d, 1, Int::plus)
                        if (h.statuses[d] == CompletionStatus.DONE) done.merge(d, 1, Int::plus)
                    }
                    d = d.plusDays(1)
                }
            }
            return DayTotals(done, due)
        }
    }
}
