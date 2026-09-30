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
        lookbackDays: Int = 90,
    ): List<TrainingRow> = histories(habits, logs, zone).flatMap { h ->
        val from = maxOf(h.start, today.minusDays(lookbackDays.toLong()))
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it.isBefore(today) }
            .filter { h.schedule.isDue(it) && h.statuses[it] != CompletionStatus.SKIPPED }
            .map { d -> TrainingRow(h.habit.id, d, features(h, d), if (h.statuses[d] == CompletionStatus.DONE) 0 else 1) }
            .toList()
    }

    /** Uses only data strictly before [date]. */
    fun features(h: HabitHistory, date: LocalDate): DoubleArray {
        val previous = ArrayList<Boolean>(28) // most recent first; true = kept
        // Walk back until 28 judged due days are found or the habit starts; the cap bounds sparse schedules.
        val stop = maxOf(h.start, date.minusDays(WALK_BACK_DAYS))
        var d = date.minusDays(1)
        while (!d.isBefore(stop) && previous.size < 28) {
            if (h.schedule.isDue(d) && h.statuses[d] != CompletionStatus.SKIPPED) previous += h.statuses[d] == CompletionStatus.DONE
            d = d.minusDays(1)
        }
        fun rate(values: List<Boolean>) = if (values.isEmpty()) 0.5 else values.count { it }.toDouble() / values.size
        val sameWeekday = (1L..4L).map { date.minusWeeks(it) }
            .filter { !it.isBefore(h.start) && h.schedule.isDue(it) && h.statuses[it] != CompletionStatus.SKIPPED }
            .map { h.statuses[it] == CompletionStatus.DONE }
        val streak = previous.takeWhile { it }.size
        return DoubleArray(FEATURE_COUNT).also { x ->
            x[date.dayOfWeek.value - 1] = 1.0
            x[F_RATE7] = rate(previous.take(7))
            x[F_RATE28] = rate(previous)
            x[F_SAME_WEEKDAY] = rate(sameWeekday)
            x[F_STREAK] = ln(1.0 + streak)
            x[F_MISSED_PREV] = if (previous.isNotEmpty() && !previous[0]) 1.0 else 0.0
            x[F_AGE] = ln(1.0 + ChronoUnit.DAYS.between(h.start, date))
            x[F_REMINDER] = if (h.habit.reminderTime != null) 1.0 else 0.0
            x[F_WEEKEND] = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) 1.0 else 0.0
        }
    }
}
