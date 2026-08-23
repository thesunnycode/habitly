package com.habitly.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "habit_reminders"

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
}
