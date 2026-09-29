package com.habitly.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "habit_reminders"

/** Tags the streak nudge so it sits beside, rather than replaces, the same habit's reminder. */
private const val STREAK_TAG = "streak_at_risk"

class NotificationHelper(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Habit Reminders", NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyHabitDue(habitId: Long, habitName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Time for: $habitName")
            .setContentText("Tap to mark it done in Habitly.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(habitId.toInt(), notification)
    }

    fun notifyStreakAtRisk(habitId: Long, habitName: String, streak: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Keep your $streak-day $habitName streak going")
            .setContentText("There's still time to check in today.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(STREAK_TAG, habitId.toInt(), notification)
    }
}
