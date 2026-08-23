package com.habitly.app

import android.app.Application
import androidx.work.Configuration
import com.habitly.app.reminder.ReminderScheduler

class HabitlyApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)
    }
}
