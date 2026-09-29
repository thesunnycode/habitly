package com.habitly.app.data.seed

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.repository.HabitRepository
import java.time.LocalDate

/**
 * Runs demo-data seeding via WorkManager rather than a raw Application-scoped coroutine.
 * A coroutine launched directly in Application.onCreate() executes immediately on a real
 * background thread and, under Robolectric, races the shared AppDatabase singleton across
 * unrelated tests (they run in the same JVM). WorkManager's own scheduling defers actual
 * execution instead of running synchronously at app-creation time, avoiding that race.
 */
class SeedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
        val prefs = SettingsPrefs(applicationContext)
        SeedDataProvider.seedAndSilenceBadges(repository, db.habitDao(), LocalDate.now(), prefs::markBadgesSeen)
        return Result.success()
    }
}
