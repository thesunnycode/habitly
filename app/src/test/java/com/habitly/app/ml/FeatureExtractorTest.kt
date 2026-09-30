package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ln

class FeatureExtractorTest {
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private val zone = ZoneOffset.UTC

    private fun habit(
        id: Long = 1, createdDaysAgo: Int, type: FrequencyType = FrequencyType.DAILY,
        days: String? = null, reminder: String? = null,
    ) = HabitEntity(
        id = id, name = "H$id", icon = "star", colorHex = "#FF922B", category = "Health",
        frequencyType = type, frequencyDays = days, intervalDays = null, reminderTime = reminder,
        createdAt = today.minusDays(createdDaysAgo.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
        isArchived = false,
    )

    private fun log(habitId: Long, date: LocalDate, status: CompletionStatus) = HabitLogEntity(
        habitId = habitId, date = date.toString(), status = status, completedAt = null, note = null,
    )

    private fun rows(habits: List<HabitEntity>, logs: List<HabitLogEntity>) =
        FeatureExtractor.rows(habits, logs, today, zone)

    private fun featuresFor(h: HabitEntity, logs: List<HabitLogEntity>, date: LocalDate) =
        FeatureExtractor.features(FeatureExtractor.histories(listOf(h), logs, zone).single(), date)

    @Test fun rowsExcludeTodayRestDaysAndDaysBeforeStart() {
        val logs = listOf(log(1, today.minusDays(4), CompletionStatus.SKIPPED))
        val result = rows(listOf(habit(createdDaysAgo = 10)), logs)
        assertEquals(9, result.size)
        assertTrue(result.none { it.date == today })
        assertTrue(result.none { it.date == today.minusDays(4) })
        assertTrue(result.none { it.date.isBefore(today.minusDays(10)) })
    }

    @Test fun labelIsOneUnlessDone() {
        val logs = listOf(
            log(1, today.minusDays(1), CompletionStatus.DONE),
            log(1, today.minusDays(2), CompletionStatus.MISSED),
            log(1, today.minusDays(3), CompletionStatus.PARTIAL),
        )
        val byDate = rows(listOf(habit(createdDaysAgo = 4)), logs).associate { it.date to it.y }
        assertEquals(0, byDate[today.minusDays(1)])
        assertEquals(1, byDate[today.minusDays(2)])
        assertEquals(1, byDate[today.minusDays(3)])
        assertEquals(1, byDate[today.minusDays(4)]) // no log
    }

    private fun featuresWithOthers(habits: List<HabitEntity>, logs: List<HabitLogEntity>, id: Long, date: LocalDate): DoubleArray {
        val histories = FeatureExtractor.histories(habits, logs, zone)
        return FeatureExtractor.features(histories.first { it.habit.id == id }, date, DayTotals.of(histories, today))
    }

    @Test fun noLookAhead() {
        val statuses = listOf(CompletionStatus.DONE, CompletionStatus.MISSED, CompletionStatus.SKIPPED, CompletionStatus.PARTIAL)
        val habits = listOf(habit(id = 1, createdDaysAgo = 30), habit(id = 2, createdDaysAgo = 30))
        val logs = (1..30).map { log(1, today.minusDays(it.toLong()), statuses[it % statuses.size]) } +
            (1..30).map { log(2, today.minusDays(it.toLong()), statuses[(it / 2) % statuses.size]) }
        for (back in 1..15) {
            val d = today.minusDays(back.toLong())
            val before = logs.filter { LocalDate.parse(it.date).isBefore(d) }
            assertArrayEquals("day $d", featuresFor(habits[0], before, d), featuresFor(habits[0], logs, d), 0.0)
            // Logs of the other habit on/after d must not change d's features either.
            val othersBefore = logs.filter { it.habitId == 1L || LocalDate.parse(it.date).isBefore(d) }
            assertArrayEquals("day $d", featuresWithOthers(habits, othersBefore, 1, d), featuresWithOthers(habits, logs, 1, d), 0.0)
            assertArrayEquals("day $d", featuresWithOthers(habits, before, 1, d), featuresWithOthers(habits, logs, 1, d), 0.0)
        }
    }

    private val twoDaily = listOf(habit(id = 1, createdDaysAgo = 10), habit(id = 2, createdDaysAgo = 10))

    private fun other(d: LocalDate, vararg statuses: CompletionStatus) =
        statuses.mapIndexed { k, st -> log(2, d.minusDays(k + 1L), st) }

    @Test fun momentumIsZeroWhenTheOtherHabitWasMissedTheLastThreeDays() {
        val d = today.minusDays(1)
        val logs = other(d, CompletionStatus.MISSED, CompletionStatus.MISSED, CompletionStatus.MISSED) + log(2, d.minusDays(4), CompletionStatus.DONE)
        assertEquals(0.0, featuresWithOthers(twoDaily, logs, 1, d)[F_MOMENTUM], 0.0) // day 4 is outside the window
    }

    @Test fun momentumIsOneWhenTheOtherHabitWasKeptTheLastThreeDays() {
        val d = today.minusDays(1)
        val logs = other(d, CompletionStatus.DONE, CompletionStatus.DONE, CompletionStatus.DONE)
        assertEquals(1.0, featuresWithOthers(twoDaily, logs, 1, d)[F_MOMENTUM], 0.0)
    }

    @Test fun momentumPoolsTheThreeDays() {
        val d = today.minusDays(1)
        // Kept yesterday, missed the day before, no log the day before that (a due day, so not kept).
        val logs = other(d, CompletionStatus.DONE, CompletionStatus.MISSED)
        assertEquals(1.0 / 3, featuresWithOthers(twoDaily, logs, 1, d)[F_MOMENTUM], 1e-12)
    }

    @Test fun momentumIsNeutralWhenNoOtherHabitWasDue() {
        val d = today.minusDays(1) // Tuesday; the three days before are Mon, Sun, Sat
        val habits = listOf(habit(id = 1, createdDaysAgo = 10), habit(id = 2, createdDaysAgo = 10, type = FrequencyType.SPECIFIC_DAYS, days = "FRIDAY"))
        val x = featuresWithOthers(habits, listOf(log(1, d.minusDays(1), CompletionStatus.DONE)), 1, d)
        assertEquals(0.5, x[F_MOMENTUM], 0.0)
        // Resting the other habit on all three days also leaves nothing to judge.
        val rested = other(d, CompletionStatus.SKIPPED, CompletionStatus.SKIPPED, CompletionStatus.SKIPPED)
        assertEquals(0.5, featuresWithOthers(twoDaily, rested, 1, d)[F_MOMENTUM], 0.0)
    }

    @Test fun ownStatusNeverAffectsItsOwnMomentum() {
        val d = today.minusDays(1)
        val others = other(d, CompletionStatus.DONE, CompletionStatus.DONE, CompletionStatus.DONE)
        for (own in listOf(null, CompletionStatus.DONE, CompletionStatus.MISSED, CompletionStatus.SKIPPED, CompletionStatus.PARTIAL)) {
            val logs = others + listOfNotNull(own?.let { log(1, d.minusDays(1), it) }, own?.let { log(1, d.minusDays(2), it) })
            assertEquals("own=$own", 1.0, featuresWithOthers(twoDaily, logs, 1, d)[F_MOMENTUM], 0.0)
        }
    }

    @Test fun rowsCarryMomentum() {
        val d = today.minusDays(1)
        val result = rows(twoDaily, other(d, CompletionStatus.DONE, CompletionStatus.DONE, CompletionStatus.DONE))
        assertEquals(1.0, result.single { it.habitId == 1L && it.date == d }.x[F_MOMENTUM], 0.0)
        assertEquals(0.0, result.single { it.habitId == 2L && it.date == d.minusDays(1) }.x[F_MOMENTUM], 0.0)
    }

    @Test fun momentumDefaultsToNeutralWithoutTotals() {
        val d = today.minusDays(1)
        assertEquals(0.5, featuresFor(habit(createdDaysAgo = 10), emptyList(), d)[F_MOMENTUM], 0.0)
    }

    @Test fun scheduledDaysSinceKept() {
        val d = today.minusDays(1)
        val h = habit(createdDaysAgo = 100)
        assertEquals(0.0, featuresFor(h, listOf(log(1, d.minusDays(1), CompletionStatus.DONE)), d)[F_SINCE_KEPT], 0.0)
        // Kept 10 days ago, then 9 judged days without a DONE; a rest day in between is not counted.
        val tenAgo = listOf(
            log(1, d.minusDays(10), CompletionStatus.DONE), log(1, d.minusDays(3), CompletionStatus.MISSED),
            log(1, d.minusDays(5), CompletionStatus.SKIPPED),
        )
        assertEquals(ln(9.0), featuresFor(h, tenAgo, d)[F_SINCE_KEPT], 1e-12)
        // Never kept: judged days since the start.
        assertEquals(ln(6.0), featuresFor(habit(createdDaysAgo = 6), emptyList(), d)[F_SINCE_KEPT], 1e-12)
        assertEquals(ln(31.0), featuresFor(h, emptyList(), d)[F_SINCE_KEPT], 1e-12)
        assertEquals(ln(31.0), featuresFor(h, listOf(log(1, d.minusDays(70), CompletionStatus.DONE)), d)[F_SINCE_KEPT], 1e-12)
    }

    @Test fun sinceKeptCountsOnlyScheduledDays() {
        // A perfectly kept Monday/Friday habit: on a Friday, nothing scheduled was missed since Monday.
        val friday = LocalDate.of(2026, 9, 25)
        val h = habit(createdDaysAgo = 30, type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY")
        val kept = (0L..4L).flatMap { w -> listOf(friday.minusWeeks(w).minusDays(4), friday.minusWeeks(w + 1)) }
            .map { log(1, it, CompletionStatus.DONE) }
        assertEquals(0.0, featuresFor(h, kept, friday)[F_SINCE_KEPT], 0.0)
        // A weekly habit that missed the last two Wednesdays after keeping the one before.
        val wednesday = LocalDate.of(2026, 9, 23)
        val weekly = habit(createdDaysAgo = 60, type = FrequencyType.SPECIFIC_DAYS, days = "WEDNESDAY")
        val logs = listOf(log(1, wednesday.minusWeeks(3), CompletionStatus.DONE), log(1, wednesday.minusWeeks(2), CompletionStatus.MISSED))
        // Due Sep 16 (no log) and Sep 9 (MISSED) since the DONE on Sep 2: 2 scheduled days, not 21 calendar days.
        assertEquals(ln(3.0), featuresFor(weekly, logs, wednesday)[F_SINCE_KEPT], 1e-12)
    }

    @Test fun featureNamesMatchTheCount() {
        assertEquals(FEATURE_COUNT, FEATURE_NAMES.size)
    }

    @Test fun trendIsPositiveAfterAGoodWeekFollowingAPoorMonth() {
        val d = today.minusDays(1)
        val logs = (1L..7L).map { log(1, d.minusDays(it), CompletionStatus.DONE) } +
            (8L..28L).map { log(1, d.minusDays(it), CompletionStatus.MISSED) }
        val x = featuresFor(habit(createdDaysAgo = 40), logs, d)
        assertEquals(1.0 - 7.0 / 28, x[F_TREND], 1e-12)
        assertTrue(x[F_TREND] > 0)
    }

    @Test fun sameWeekdayUsesTwentyWeeksSmoothedTowardsTheLongRunRate() {
        val d = today.minusDays(1)
        val logs = (1L..5L).map { log(1, d.minusWeeks(it), CompletionStatus.DONE) } +
            (6L..20L).map { log(1, d.minusWeeks(it), CompletionStatus.MISSED) } +
            log(1, d.minusWeeks(21), CompletionStatus.DONE) // outside the window
        val x = featuresFor(habit(createdDaysAgo = 200), logs, d)
        // Last 90 due days: the 5 kept same weekdays (weeks 1-5 are within 35 days), the rest not kept.
        val rate90 = (5 + 1.0) / (90 + 2)
        assertEquals(rate90, x[F_RATE90], 1e-12)
        assertEquals((5 + 4 * rate90) / (20 + 4), x[F_SAME_WEEKDAY], 1e-12)
    }

    @Test fun rate90IsSmoothedAndCapped() {
        val d = today.minusDays(1)
        val logs = (1L..45L).map { log(1, d.minusDays(it), CompletionStatus.DONE) } +
            (91L..100L).map { log(1, d.minusDays(it), CompletionStatus.DONE) } // beyond 90 due days
        assertEquals(46.0 / 92, featuresFor(habit(createdDaysAgo = 200), logs, d)[F_RATE90], 1e-12)
        val short = (1L..3L).map { log(1, d.minusDays(it), CompletionStatus.DONE) }
        assertEquals(4.0 / 5, featuresFor(habit(createdDaysAgo = 4), short, d)[F_RATE90], 1e-12)
    }

    @Test fun restedPrevious() {
        val d = today.minusDays(1) // Tuesday
        val h = habit(createdDaysAgo = 20)
        assertEquals(1.0, featuresFor(h, listOf(log(1, d.minusDays(1), CompletionStatus.SKIPPED)), d)[F_RESTED_PREV], 0.0)
        assertEquals(0.0, featuresFor(h, listOf(log(1, d.minusDays(1), CompletionStatus.DONE), log(1, d.minusDays(2), CompletionStatus.SKIPPED)), d)[F_RESTED_PREV], 0.0)
        // Only due days count: the previous due day of a Tuesday/Friday habit is last Friday.
        val specific = habit(createdDaysAgo = 20, type = FrequencyType.SPECIFIC_DAYS, days = "TUESDAY,FRIDAY")
        assertEquals(1.0, featuresFor(specific, listOf(log(1, d.minusDays(4), CompletionStatus.SKIPPED)), d)[F_RESTED_PREV], 0.0)
    }

    @Test fun defaultsWithoutHistory() {
        val first = rows(listOf(habit(createdDaysAgo = 5)), emptyList()).first()
        assertEquals(0.5, first.x[F_RATE7], 0.0)
        assertEquals(0.5, first.x[F_RATE28], 0.0)
        assertEquals(0.5, first.x[F_SAME_WEEKDAY], 0.0)
        assertEquals(0.5, first.x[F_RATE90], 0.0)
        assertEquals(0.0, first.x[F_RESTED_PREV], 0.0)
        assertEquals(0.0, first.x[F_STREAK], 0.0)
        assertEquals(0.0, first.x[F_MISSED_PREV], 0.0)
    }

    @Test fun streakAndMissedPrev() {
        val d = today.minusDays(1)
        val h = habit(createdDaysAgo = 10)
        val done = (1L..3L).map { log(1, d.minusDays(it), CompletionStatus.DONE) }
        val kept = featuresFor(h, done, d)
        assertEquals(ln(4.0), kept[F_STREAK], 1e-12)
        assertEquals(0.0, kept[F_MISSED_PREV], 0.0)

        val withMiss = done.filter { it.date != d.minusDays(1).toString() } + log(1, d.minusDays(1), CompletionStatus.MISSED)
        val missed = featuresFor(h, withMiss, d)
        assertEquals(1.0, missed[F_MISSED_PREV], 0.0)
        assertEquals(0.0, missed[F_STREAK], 0.0)
    }

    @Test fun weekdayOneHotAndWeekend() {
        val saturday = rows(listOf(habit(createdDaysAgo = 20)), emptyList()).first { it.date.dayOfWeek == DayOfWeek.SATURDAY }
        for (i in 0..6) assertEquals(if (i == 5) 1.0 else 0.0, saturday.x[i], 0.0)
        assertEquals(1.0, saturday.x[F_WEEKEND], 0.0)
    }

    @Test fun specificDaysOnlyDueDays() {
        val h = habit(createdDaysAgo = 30, type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY")
        val result = rows(listOf(h), emptyList())
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.date.dayOfWeek == DayOfWeek.MONDAY || it.date.dayOfWeek == DayOfWeek.FRIDAY })
    }

    @Test fun lookbackCapped() {
        val result = rows(listOf(habit(createdDaysAgo = 300)), emptyList())
        assertEquals(today.minusDays(180), result.minOf { it.date })
    }

    @Test fun startIsEarlierOfCreationAndFirstLog() {
        val early = today.minusDays(15)
        val result = rows(listOf(habit(createdDaysAgo = 10)), listOf(log(1, early, CompletionStatus.DONE)))
        assertTrue(result.any { it.date == early })
        assertEquals(0, result.first { it.date == early }.y)
    }

    @Test fun skippedDaysAreNeitherKeptNorMissed() {
        val d = today.minusDays(1)
        val logs = listOf(
            log(1, d.minusDays(3), CompletionStatus.DONE),
            log(1, d.minusDays(2), CompletionStatus.SKIPPED),
            log(1, d.minusDays(1), CompletionStatus.DONE),
        )
        val x = featuresFor(habit(createdDaysAgo = 4), logs, d)
        assertEquals(ln(3.0), x[F_STREAK], 1e-12)
        assertEquals(1.0, x[F_RATE7], 0.0)
    }

    @Test fun nonDueDaysAreIgnored() {
        // Created on a Friday, so no Monday is due before the first Monday row.
        val friday = LocalDate.of(2026, 9, 25)
        val h = habit(createdDaysAgo = 5, type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY")
        val monday = LocalDate.of(2026, 9, 28)
        val x = featuresFor(h, listOf(log(1, friday, CompletionStatus.DONE)), monday)
        assertEquals(1.0, x[F_RATE7], 0.0)
        // No Monday yet, so the same-weekday rate is just the prior: the long-run rate (1 + 1) / (1 + 2).
        assertEquals(2.0 / 3, x[F_SAME_WEEKDAY], 1e-12)
    }

    @Test fun intervalAnchorsOnCreationDayNotBackfilledLog() {
        val h = habit(createdDaysAgo = 6, type = FrequencyType.INTERVAL).copy(intervalDays = 2)
        // Created Sep 24; the backfill on Sep 21 has the opposite parity, so anchoring on it would
        // make Sep 21/23/25/27/29 due instead.
        val backfill = log(1, today.minusDays(9), CompletionStatus.DONE)
        val dates = rows(listOf(h), listOf(backfill)).map { it.date }
        assertEquals(listOf(24, 26, 28).map { LocalDate.of(2026, 9, it) }, dates)
    }
}
