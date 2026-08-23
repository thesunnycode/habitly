package com.habitly.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habitly.app.data.seed.SeedWorker
import com.habitly.app.reminder.ReminderScheduler

class HabitlyApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)

        WorkManager.getInstance(this).enqueueUniqueWork(
            "seed_demo_data",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SeedWorker>().build(),
        )
    }
}
