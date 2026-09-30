package com.habitly.app.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.ai.AiServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForecastTrainWorkerTest {

    // Both keep static singletons, and Robolectric resets SQLite between tests, so start and end fresh.
    @Before @After
    fun resetSingletons() {
        AppDatabase.resetForTests()
        ForecastServices.resetForTests()
    }

    @Test
    fun doWorkSucceedsOnAnEmptyDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startedAt = System.currentTimeMillis()
        val worker = TestListenableWorkerBuilder<ForecastTrainWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val saved = AiServices.prefs(context).forecastModelJson.first()
        assertNotNull(saved)
        val model = Json { ignoreUnknownKeys = true }.decodeFromString(RiskModel.serializer(), saved!!)
        assertTrue(model.trainedAt >= startedAt)
    }
}
