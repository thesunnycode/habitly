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

    @Test fun noLookAhead() {
        val statuses = listOf(CompletionStatus.DONE, CompletionStatus.MISSED, CompletionStatus.SKIPPED, CompletionStatus.PARTIAL)
        val logs = (1..30).map { log(1, today.minusDays(it.toLong()), statuses[it % statuses.size]) }
        val h = habit(createdDaysAgo = 30)
        for (back in 1..15) {
            val d = today.minusDays(back.toLong())
            val before = logs.filter { LocalDate.parse(it.date).isBefore(d) }
            assertArrayEquals("day $d", featuresFor(h, before, d), featuresFor(h, logs, d), 0.0)
        }
    }

    @Test fun defaultsWithoutHistory() {
        val first = rows(listOf(habit(createdDaysAgo = 5)), emptyList()).first()
        assertEquals(0.5, first.x[F_RATE7], 0.0)
        assertEquals(0.5, first.x[F_RATE28], 0.0)
        assertEquals(0.5, first.x[F_SAME_WEEKDAY], 0.0)
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
        val result = rows(listOf(habit(createdDaysAgo = 200)), emptyList())
        assertTrue(result.minOf { it.date } >= today.minusDays(90))
    }

    @Test fun startIsEarlierOfCreationAndFirstLog() {
        val early = today.minusDays(15)
        val result = rows(listOf(habit(createdDaysAgo = 10)), listOf(log(1, early, CompletionStatus.DONE)))
        assertTrue(result.any { it.date == early })
        assertEquals(0, result.first { it.date == early }.y)
    }
}
