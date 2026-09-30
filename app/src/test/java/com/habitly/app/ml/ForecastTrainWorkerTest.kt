package com.habitly.app.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.habitly.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForecastTrainWorkerTest {

    // AppDatabase and ForecastServices keep static singletons that outlive Robolectric's per-test SQLite
    // reset, so a database opened by an earlier test class would hand this one a dead connection.
    @Before @After
    fun resetSingletons() {
        (staticField(AppDatabase::class.java, "INSTANCE") as AppDatabase?)?.close()
        setStatic(AppDatabase::class.java, "INSTANCE")
        setStatic(ForecastServices::class.java, "instance")
    }

    private fun staticField(owner: Class<*>, name: String): Any? =
        owner.getDeclaredField(name).apply { isAccessible = true }.get(null)

    private fun setStatic(owner: Class<*>, name: String) {
        owner.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun doWorkSucceedsOnAnEmptyDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<ForecastTrainWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
