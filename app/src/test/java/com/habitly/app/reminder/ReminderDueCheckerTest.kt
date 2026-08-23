package com.habitly.app.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
