package com.habitly.app.ai

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.StreakCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Turns habits and logs into the anonymised summaries the proxy receives. Only counts, streaks and
 * habit metadata leave the device; log notes and timestamps never do.
 */
object AiContextBuilder {
    const val MAX_HABITS = 20

    fun build(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        asOf: LocalDate,
        risks: Map<Long, Double> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HabitSummaryDto> {
        val byHabit = logs.groupBy { it.habitId }
        return habits.filter { !it.isArchived }.sortedBy { it.sortOrder }.take(MAX_HABITS).map { habit ->
            val habitLogs = byHabit[habit.id].orEmpty()
            val statuses = habitLogs.associate { LocalDate.parse(it.date) to it.status }
            val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
            // Days before this were never the habit's; they are neither due nor missed. A log dated
            // before the creation day (backfilled) pulls the start earlier for DAILY/SPECIFIC_DAYS habits.
            val start = (statuses.keys + created).min()
            // INTERVAL habits stay anchored on the creation day, never on `start`, matching
            // HabitRepository, so their due days agree with the rest of the app.
            val schedule = HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, created)
            fun counts(from: LocalDate, to: LocalDate): CountsDto {
                var done = 0; var rested = 0; var missed = 0; var due = 0
                var d = from
                while (!d.isAfter(to)) {
                    if (!d.isBefore(start) && schedule.isDue(d)) {
                        due++
                        when (statuses[d]) {
                            CompletionStatus.DONE -> done++
                            CompletionStatus.SKIPPED -> rested++
                            // Today is still in progress: nothing, PARTIAL or MISSED on it is not a miss yet.
                            else -> if (d.isBefore(asOf)) missed++
                        }
                    }
                    d = d.plusDays(1)
                }
                return CountsDto(done, rested, missed, due)
            }
            val weekdays = DayOfWeek.entries.map { day ->
                var done = 0; var due = 0
                for (i in 0 until 28) {
                    val d = asOf.minusDays(i.toLong())
                    if (d.dayOfWeek == day && !d.isBefore(start) && schedule.isDue(d)) {
                        due++
                        if (statuses[d] == CompletionStatus.DONE) done++
                    }
                }
                WeekdayStatDto(day.name, done, due)
            }
            val records = statuses.map { (d, s) -> CompletionRecord(d, s) }.sortedBy { it.date }
            val hours = habitLogs
                .filter {
                    it.status == CompletionStatus.DONE && it.completedAt != null &&
                        !LocalDate.parse(it.date).isBefore(asOf.minusDays(27))
                }
                .map { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).hour }
            HabitSummaryDto(
                id = habit.id, name = habit.name, category = habit.category,
                frequency = FrequencyDto(
                    type = habit.frequencyType.name,
                    // Parsed through the schedule so only valid DayOfWeek names reach the wire.
                    days = schedule.daysOfWeek.takeIf { habit.frequencyType == FrequencyType.SPECIFIC_DAYS }
                        ?.sorted()?.map { it.name },
                    intervalDays = habit.intervalDays,
                ),
                targetCount = habit.targetCount, unit = habit.unit?.takeIf { it.isNotBlank() },
                reminderTime = habit.reminderTime,
                createdDaysAgo = ChronoUnit.DAYS.between(created, asOf).toInt().coerceAtLeast(0),
                currentStreak = StreakCalculator.currentStreak(records, asOf, schedule),
                bestStreak = StreakCalculator.bestStreak(records),
                last7 = counts(asOf.minusDays(6), asOf),
                prev7 = counts(asOf.minusDays(13), asOf.minusDays(7)),
                weekdays = weekdays,
                avgCheckInHour = if (hours.isEmpty()) null else hours.average().roundToInt(),
                dueToday = !asOf.isBefore(start) && schedule.isDue(asOf),
                todayStatus = statuses[asOf]?.name,
                risk = risks[habit.id],
            )
        }
    }
}
