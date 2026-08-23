package com.habitly.app.reminder

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
}
