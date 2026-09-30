package com.habitly.app

import android.app.Application
import androidx.work.Configuration
import com.habitly.app.ml.ForecastScheduler
import com.habitly.app.reminder.ReminderScheduler

class HabitlyApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)
        ForecastScheduler.schedule(this)
        // No auto-seeding: a new install starts empty. Demo data is a debug-only action in Settings.
    }
}
