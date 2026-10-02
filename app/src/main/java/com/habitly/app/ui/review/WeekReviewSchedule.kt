package com.habitly.app.ui.review

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** When a week is reviewable and when the Today banner advertises it. */
object WeekReviewSchedule {
    const val MIN_TRACKING_DAYS = 3
    private val READY_AT = LocalTime.of(18, 0)
    private const val BANNER_DAYS_INTO_NEXT_WEEK = 1L

    fun currentWeekStart(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

    /** The current week from its last day at 18:00, otherwise the previous week. */
    fun reviewWeekStart(now: LocalDateTime, weekStart: DayOfWeek): LocalDate {
        val start = currentWeekStart(now.toLocalDate(), weekStart)
        val ready = now.toLocalDate() == start.plusDays(6) && !now.toLocalTime().isBefore(READY_AT)
        return if (ready) start else start.minusWeeks(1)
    }

    /** The last day the review covers: the week's end, or today while the week is still running. */
    fun reviewAsOf(reviewStart: LocalDate, today: LocalDate): LocalDate = minOf(reviewStart.plusDays(6), today)

    fun bannerVisible(now: LocalDateTime, weekStart: DayOfWeek, dismissedWeek: String?, trackingDays: Int): Boolean {
        if (trackingDays < MIN_TRACKING_DAYS) return false
        val reviewStart = reviewWeekStart(now, weekStart)
        if (dismissedWeek == reviewStart.toString()) return false
        // Ready from the last day at 18:00 (already implied by reviewStart) until the new week's second day.
        return !now.toLocalDate().isAfter(reviewStart.plusDays(7 + BANNER_DAYS_INTO_NEXT_WEEK))
    }

    /** Days from the earliest habit creation or log up to today, inclusive; 0 with nothing tracked. */
    fun trackingDays(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val starts = habits.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() } +
            logs.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val earliest = starts.minOrNull() ?: return 0
        return (ChronoUnit.DAYS.between(earliest, today) + 1).toInt().coerceAtLeast(0)
    }
}
