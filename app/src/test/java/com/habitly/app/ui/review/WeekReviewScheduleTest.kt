package com.habitly.app.ui.review

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class WeekReviewScheduleTest {
    private val monday = DayOfWeek.MONDAY

    private fun at(date: String, time: String = "12:00") = LocalDateTime.parse("${date}T$time")

    private fun visible(now: LocalDateTime, dismissed: String? = null, days: Int = 10, weekStart: DayOfWeek = monday) =
        WeekReviewSchedule.bannerVisible(now, weekStart, dismissed, days)

    @Test fun currentWeekStartSnapsBackToTheWeekStartDay() {
        assertEquals(LocalDate.of(2026, 9, 28), WeekReviewSchedule.currentWeekStart(LocalDate.of(2026, 10, 3), monday))
        assertEquals(LocalDate.of(2026, 9, 28), WeekReviewSchedule.currentWeekStart(LocalDate.of(2026, 9, 28), monday))
        assertEquals(LocalDate.of(2026, 9, 27), WeekReviewSchedule.currentWeekStart(LocalDate.of(2026, 10, 3), DayOfWeek.SUNDAY))
    }

    @Test fun reviewWeekIsThePreviousOneUntilTheLastDayAtSix() {
        assertEquals(LocalDate.of(2026, 9, 21), WeekReviewSchedule.reviewWeekStart(at("2026-10-04", "17:59"), monday))
        assertEquals(LocalDate.of(2026, 9, 28), WeekReviewSchedule.reviewWeekStart(at("2026-10-04", "18:00"), monday))
        assertEquals(LocalDate.of(2026, 9, 28), WeekReviewSchedule.reviewWeekStart(at("2026-10-05", "09:00"), monday))
        assertEquals(LocalDate.of(2026, 9, 27), WeekReviewSchedule.reviewWeekStart(at("2026-10-03", "18:30"), DayOfWeek.SUNDAY))
    }

    @Test fun reviewAsOfIsTheWeekEndOrToday() {
        assertEquals(LocalDate.of(2026, 10, 4), WeekReviewSchedule.reviewAsOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
        assertEquals(LocalDate.of(2026, 9, 27), WeekReviewSchedule.reviewAsOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 5)))
    }

    @Test fun bannerShowsFromSundayEveningUntilTwoDaysIntoTheNewWeek() {
        assertTrue(visible(at("2026-10-04", "18:00")))
        assertTrue(visible(at("2026-10-04", "23:30")))
        assertTrue(visible(at("2026-10-05", "08:00")))
        assertTrue(visible(at("2026-10-06", "23:59")))
        assertFalse(visible(at("2026-10-07", "09:00")))
        assertFalse(visible(at("2026-10-04", "17:59")))
        assertFalse(visible(at("2026-10-01", "12:00")))
    }

    @Test fun bannerHiddenWhenDismissedForThatWeekOrTooLittleHistory() {
        assertFalse(visible(at("2026-10-05"), dismissed = "2026-09-28"))
        assertTrue(visible(at("2026-10-05"), dismissed = "2026-09-21"))
        assertFalse(visible(at("2026-10-05"), days = 2))
        assertTrue(visible(at("2026-10-05"), days = WeekReviewSchedule.MIN_TRACKING_DAYS))
    }

    @Test fun bannerFollowsAnotherWeekStart() {
        // Sunday-start weeks end on Saturday.
        assertTrue(visible(at("2026-10-03", "18:00"), weekStart = DayOfWeek.SUNDAY))
        assertTrue(visible(at("2026-10-05"), weekStart = DayOfWeek.SUNDAY))
        assertFalse(visible(at("2026-10-06"), weekStart = DayOfWeek.SUNDAY))
    }

    private fun habit(createdOn: LocalDate) = HabitEntity(
        name = "h", icon = "star", colorHex = "#FFFFFF", category = "General",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
        createdAt = createdOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), isArchived = false,
    )

    private fun log(date: LocalDate) =
        HabitLogEntity(habitId = 1, date = date.toString(), status = CompletionStatus.DONE, completedAt = null, note = null)

    @Test fun trackingDaysCountsInclusivelyFromTheEarliestSign() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals(3, WeekReviewSchedule.trackingDays(listOf(habit(today.minusDays(2))), emptyList(), today, ZoneOffset.UTC))
        assertEquals(0, WeekReviewSchedule.trackingDays(emptyList(), emptyList(), today, ZoneOffset.UTC))
        // A backfilled log older than the habit pulls the start earlier.
        assertEquals(11, WeekReviewSchedule.trackingDays(listOf(habit(today.minusDays(2))), listOf(log(today.minusDays(10))), today, ZoneOffset.UTC))
        assertEquals(1, WeekReviewSchedule.trackingDays(listOf(habit(today)), emptyList(), today, ZoneOffset.UTC))
    }
}
