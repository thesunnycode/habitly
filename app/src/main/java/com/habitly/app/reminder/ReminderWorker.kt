package com.habitly.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.repository.HabitRepository
import com.habitly.app.domain.CompletionStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = SettingsPrefs(applicationContext)
        if (!prefs.notificationsEnabled.first()) return Result.success()
        val nudgeEnabled = prefs.streakNudgeEnabled.first()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
        val habits = repository.observeHabitsWithStats(LocalDate.now()).first()
        val now = LocalTime.now()
        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.ensureChannel()

        habits.forEach { habitWithStats ->
            val habit = habitWithStats.habit
            val due = ReminderDueChecker.isDue(
                reminderTime = habit.reminderTime,
                isCompletedToday = habitWithStats.isCompletedToday,
                nowHourMinute = now.hour to now.minute,
            )
            if (due) {
                notificationHelper.notifyHabitDue(habit.id, habit.name)
            }

            val reminder = habit.reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            if (nudgeEnabled && reminder != null &&
                ReminderDueChecker.isStreakNudgeDue(
                    reminder = reminder,
                    now = now,
                    streak = habitWithStats.currentStreak,
                    doneToday = habitWithStats.isCompletedToday,
                    dueToday = habitWithStats.isDueToday,
                    restedToday = habitWithStats.todayStatus == CompletionStatus.SKIPPED,
                )
            ) {
                notificationHelper.notifyStreakAtRisk(habit.id, habit.name, habitWithStats.currentStreak)
            }
        }
        return Result.success()
    }
}
