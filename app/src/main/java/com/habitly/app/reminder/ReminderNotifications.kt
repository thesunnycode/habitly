package com.habitly.app.reminder

/**
 * Clears a habit's posted reminder and streak nudge once it has been logged for today, so a
 * "Time for: Read" notification doesn't linger after the user has already read.
 */
fun interface ReminderNotifications {
    fun cancelFor(habitId: Long)

    companion object {
        /** For callers (and tests) that have no notifications to clear. */
        val None = ReminderNotifications { }
    }
}
