package com.habitly.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.repository.HabitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = SettingsPrefs(applicationContext)
        if (!prefs.notificationsEnabled.first()) return Result.success()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
        val habits = repository.observeHabitsWithStats(LocalDate.now()).first()
        val now = LocalTime.now()
        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.ensureChannel()

        habits.forEach { habitWithStats ->
            val due = ReminderDueChecker.isDue(
                reminderTime = habitWithStats.habit.reminderTime,
                isCompletedToday = habitWithStats.isCompletedToday,
                nowHourMinute = now.hour to now.minute,
            )
            if (due) {
                notificationHelper.notifyHabitDue(habitWithStats.habit.id, habitWithStats.habit.name)
            }
        }
        return Result.success()
    }
}
