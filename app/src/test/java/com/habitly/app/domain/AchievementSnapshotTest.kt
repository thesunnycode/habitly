package com.habitly.app.domain

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AchievementSnapshotTest {
    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 8, 1)
    private fun habit(id: Long, target: Int? = null, archived: Boolean = false) = HabitEntity(
        id = id, name = "H$id", icon = "star", colorHex = "#FF922B", category = "X",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
        createdAt = start.atStartOfDay(zone).toInstant().toEpochMilli(), isArchived = archived, targetCount = target,
    )
    private fun log(habitId: Long, date: LocalDate, status: CompletionStatus = CompletionStatus.DONE, hour: Int = 12) =
        HabitLogEntity(0, habitId, date.toString(), status,
            if (status == CompletionStatus.DONE) date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli() else null, null)

    @Test fun countsCheckInsEarlyBirdsAndRestDays() {
        val logs = listOf(log(1, start, hour = 7), log(1, start.plusDays(1), hour = 9), log(1, start.plusDays(2), CompletionStatus.SKIPPED))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(3), zone)
        assertEquals(2, s.totalCheckIns); assertEquals(1, s.earlyCheckIns); assertEquals(1, s.restDays)
    }
    @Test fun perfectDaysNeedEveryDueHabitKept() {
        val d = start.plusDays(5)
        val logs = listOf(log(1, d), log(2, d), log(1, d.plusDays(1)))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1), habit(2)), logs, d.plusDays(2), zone)
        assertEquals(1, s.perfectDays); assertEquals(1, s.longestPerfectRun)
    }
    @Test fun perfectRunCountsConsecutiveDays() {
        val logs = (0L..6L).map { log(1, start.plusDays(it)) }
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(7), zone)
        assertEquals(7, s.longestPerfectRun)
    }
    @Test fun goalDaysOnlyCountGoalHabits() {
        val logs = listOf(log(1, start), log(2, start))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1, target = 8), habit(2)), logs, start.plusDays(1), zone)
        assertEquals(1, s.goalDaysMet)
    }
    @Test fun bestMonthUsesOnlyFinishedMonths() {
        val logs = (0L until 31L).filter { it % 10 != 0L }.map { log(1, start.plusDays(it)) } // Aug: 27 of 31
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, LocalDate.of(2026, 9, 29), zone)
        assertEquals(87, s.bestMonthPercent)
    }
    @Test fun comebackAfterABrokenStreakOfThree() {
        val logs = listOf(log(1, start), log(1, start.plusDays(1)), log(1, start.plusDays(2)), log(1, start.plusDays(4)))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(5), zone)
        assertEquals(1, s.comebacks)
    }
    @Test fun habitCountsSeparateActiveFromEver() {
        val s = AchievementSnapshotBuilder.build(listOf(habit(1), habit(2, archived = true)), emptyList(), start, zone)
        assertEquals(2, s.habitsEver); assertEquals(1, s.activeHabits)
    }
}
