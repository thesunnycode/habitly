package com.habitly.app.reminder

import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.habitly.app.data.prefs.SettingsPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {

    @Test
    fun doWorkSucceedsWhenNotificationsAreDisabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsPrefs(context).setNotificationsEnabled(false)
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        val result = worker.doWork()
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWorkSucceedsAndPostsNoCrashWithNoHabits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsPrefs(context).setNotificationsEnabled(true)
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        val result = worker.doWork()
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }
}
