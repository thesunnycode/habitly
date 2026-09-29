package com.habitly.app.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderDueCheckerTest {

    @Test
    fun notDueWhenNoReminderTimeIsSet() {
        assertFalse(ReminderDueChecker.isDue(null, isCompletedToday = false, nowHourMinute = 8 to 5))
    }

    @Test
    fun notDueWhenAlreadyCompletedToday() {
        assertFalse(ReminderDueChecker.isDue("08:00", isCompletedToday = true, nowHourMinute = 8 to 5))
    }

    @Test
    fun dueWhenNowIsWithinWindowAfterReminderTime() {
        assertTrue(ReminderDueChecker.isDue("08:00", isCompletedToday = false, nowHourMinute = 8 to 10))
    }

    @Test
    fun notDueWhenNowIsBeforeReminderTime() {
        assertFalse(ReminderDueChecker.isDue("08:00", isCompletedToday = false, nowHourMinute = 7 to 59))
    }

    @Test
    fun notDueWhenNowIsPastTheWindow() {
        assertFalse(ReminderDueChecker.isDue("08:00", isCompletedToday = false, nowHourMinute = 8 to 20))
    }

    @Test
    fun streakNudgeFiresHalfAnHourAfterTheReminder() {
        assertTrue(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), streak = 3, doneToday = false, dueToday = true))
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 10), 3, false, true))
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 1, false, true))
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 3, true, true))
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 3, false, false))
    }

    @Test
    fun streakNudgeWindowIsHalfOpenFromThirtyToFortyFiveMinutes() {
        assertTrue(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 30), 2, false, true))
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 45), 2, false, true))
    }

    @Test
    fun streakNudgeNeverSpillsPastMidnightIntoTheNextDay() {
        // 23:40 + 30 min is 00:10 tomorrow, when "today" and its streak are a different day.
        assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(23, 40), LocalTime.of(0, 15), 5, false, true))
    }
}
