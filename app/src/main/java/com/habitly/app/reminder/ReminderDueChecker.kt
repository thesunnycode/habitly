package com.habitly.app.reminder

import java.time.LocalTime

object ReminderDueChecker {

    fun isDue(
        reminderTime: String?,
        isCompletedToday: Boolean,
        nowHourMinute: Pair<Int, Int>,
        windowMinutes: Int = 15,
    ): Boolean {
        if (reminderTime == null || isCompletedToday) return false
        val (reminderHour, reminderMinute) = reminderTime.split(":").map { it.toInt() }
        val reminderTotalMinutes = reminderHour * 60 + reminderMinute
        val (nowHour, nowMinute) = nowHourMinute
        val nowTotalMinutes = nowHour * 60 + nowMinute
        val delta = nowTotalMinutes - reminderTotalMinutes
        return delta in 0 until windowMinutes
    }

    /**
     * The streak-at-risk nudge: 30 to 45 minutes after the reminder (the worker runs about every
     * 15 minutes, so this fires once), for a habit due and not done today with a streak of 2 or more.
     * A window that would cross midnight never fires — by then "today" and its streak have moved on.
     * A declared rest day ([restedToday]) cannot break a streak, so it never nudges either.
     */
    fun isStreakNudgeDue(
        reminder: LocalTime,
        now: LocalTime,
        streak: Int,
        doneToday: Boolean,
        dueToday: Boolean,
        restedToday: Boolean = false,
    ): Boolean {
        if (restedToday) return false
        val start = reminder.plusMinutes(30)
        val end = reminder.plusMinutes(45)
        if (start.isBefore(reminder) || end.isBefore(reminder)) return false
        return dueToday && !doneToday && streak >= 2 && !now.isBefore(start) && now.isBefore(end)
    }
}
