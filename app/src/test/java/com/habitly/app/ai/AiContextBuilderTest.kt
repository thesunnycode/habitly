package com.habitly.app.ai

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.StreakCalculator
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class AiContextBuilderTest {
    private val asOf = LocalDate.of(2026, 9, 30)
    private val zone = ZoneOffset.UTC

    private fun millis(d: LocalDate, hour: Int = 0) = d.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun habit(
        id: Long = 1, createdDaysAgo: Long = 20, type: FrequencyType = FrequencyType.DAILY, days: String? = null,
        archived: Boolean = false, sortOrder: Int = 0, name: String = "Read",
    ) = HabitEntity(
        id = id, name = name, icon = "star", colorHex = "#fff", category = "Mind", frequencyType = type,
        frequencyDays = days, intervalDays = null, reminderTime = "21:00",
        createdAt = millis(asOf.minusDays(createdDaysAgo)), isArchived = archived, sortOrder = sortOrder,
    )

    private fun log(d: LocalDate, status: CompletionStatus, hour: Int? = 8, note: String? = null, habitId: Long = 1) =
        HabitLogEntity(
            habitId = habitId, date = d.toString(), status = status,
            completedAt = hour?.let { millis(d, it) }, note = note,
        )

    private fun build(habits: List<HabitEntity>, logs: List<HabitLogEntity>, risks: Map<Long, Double> = emptyMap()) =
        AiContextBuilder.build(habits, logs, asOf, risks, zone)

    // Last 7 days are 09-24..09-30: 5 done, 1 skipped, 09-24 untouched (a past day, so missed).
    // Previous 7 days are 09-17..09-23: 3 done.
    private val standardLogs: List<HabitLogEntity> = buildList {
        for (i in 0..4) add(log(asOf.minusDays(i.toLong()), CompletionStatus.DONE, hour = 8))
        add(log(asOf.minusDays(5), CompletionStatus.SKIPPED, hour = null))
        for (i in 13 downTo 11) add(log(asOf.minusDays(i.toLong()), CompletionStatus.DONE, hour = 10))
    }

    @Test fun countsAndStreaksForDailyHabit() {
        val s = build(listOf(habit()), standardLogs, mapOf(1L to 0.4)).single()
        assertEquals(CountsDto(done = 5, rested = 1, missed = 1, due = 7), s.last7)
        assertEquals(3, s.prev7.done)
        assertEquals(20, s.createdDaysAgo)
        assertEquals(0.4, s.risk!!, 0.0)
        assertTrue(s.dueToday)
        assertEquals("DONE", s.todayStatus)

        val records = standardLogs.map { CompletionRecord(LocalDate.parse(it.date), it.status) }
        val schedule = HabitSchedule.parse(FrequencyType.DAILY, null, null, asOf.minusDays(20))
        assertEquals(StreakCalculator.currentStreak(records, asOf, schedule), s.currentStreak)
        assertEquals(StreakCalculator.bestStreak(records), s.bestStreak)
    }

    @Test fun weekdaysCoverLast28DaysMondayFirst() {
        val s = build(listOf(habit()), standardLogs).single()
        assertEquals(DayOfWeek.entries.map { it.name }, s.weekdays.map { it.day })
        // Created 09-10: 21 due days inside the window, 3 per weekday; 09-03..09-09 predate the habit.
        assertTrue(s.weekdays.all { it.due == 3 })
        assertEquals(1, s.weekdays.first { it.day == "WEDNESDAY" }.done)
    }

    @Test fun avgCheckInHourIsRoundedMeanOfDoneLogs() {
        assertEquals(9, build(listOf(habit()), standardLogs).single().avgCheckInHour)
        assertNull(build(listOf(habit()), emptyList()).single().avgCheckInHour)
    }

    @Test fun daysBeforeStartAreNeitherDueNorMissed() {
        val s = build(listOf(habit(createdDaysAgo = 5)), emptyList()).single()
        assertEquals(CountsDto(done = 0, rested = 0, missed = 5, due = 6), s.last7)
        assertEquals(CountsDto(0, 0, 0, 0), s.prev7)
    }

    @Test fun logBeforeCreationMovesStartEarlierForDailyHabit() {
        // Created 09-25 but backfilled a DONE on 09-22: 09-22 and 09-23 now count as due.
        val logs = listOf(log(asOf.minusDays(8), CompletionStatus.DONE))
        val s = build(listOf(habit(createdDaysAgo = 5)), logs).single()
        assertEquals(CountsDto(done = 1, rested = 0, missed = 1, due = 2), s.prev7)
        assertEquals(CountsDto(done = 0, rested = 0, missed = 6, due = 7), s.last7)
    }

    @Test fun partialTodayIsNotMissed() {
        val logs = listOf(log(asOf, CompletionStatus.PARTIAL, hour = null))
        val s = build(listOf(habit(createdDaysAgo = 20)), logs).single()
        // 09-24..09-29 are past and unlogged; today is still in progress.
        assertEquals(CountsDto(done = 0, rested = 0, missed = 6, due = 7), s.last7)
    }

    @Test fun partialOnPastDayIsMissed() {
        val logs = listOf(log(asOf.minusDays(1), CompletionStatus.PARTIAL, hour = null))
        val s = build(listOf(habit(createdDaysAgo = 20)), logs).single()
        assertEquals(6, s.last7.missed)
    }

    @Test fun invalidDayNamesAreDroppedAndOrdered() {
        val s = build(listOf(habit(type = FrequencyType.SPECIFIC_DAYS, days = "FRIDAY,BOGUS,MONDAY")), emptyList()).single()
        assertEquals(listOf("MONDAY", "FRIDAY"), s.frequency.days)
        assertNull(build(listOf(habit()), emptyList()).single().frequency.days)
    }

    @Test fun specificDaysFrequencyIsMapped() {
        val s = build(listOf(habit(type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY")), emptyList()).single()
        assertEquals(FrequencyDto("SPECIFIC_DAYS", listOf("MONDAY", "FRIDAY"), null), s.frequency)
        // asOf is a Wednesday, so nothing is due today.
        assertFalse(s.dueToday)
    }

    @Test fun archivedExcludedAndCappedAtTwentyBySortOrder() {
        val habits = (1L..25L).map { habit(id = it, sortOrder = (30 - it).toInt(), name = "H$it") } +
            habit(id = 99, archived = true, sortOrder = -1, name = "Old")
        val result = build(habits, emptyList())
        assertEquals(20, result.size)
        assertFalse(result.any { it.name == "Old" })
        assertEquals(25L, result.first().id)
    }

    @Test fun notesNeverLeaveTheDevice() {
        val logs = standardLogs + log(asOf.minusDays(7), CompletionStatus.DONE, note = "secret-note")
        val json = AiJson.encodeToString(build(listOf(habit()), logs))
        assertFalse(json.contains("secret"))
    }
}
