package com.habitly.app.ml

import android.content.Context
import com.habitly.app.ai.AiServices
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

/**
 * Keeps the stored risk model and turns it into today's forecast. Prediction is cheap and re-runs on
 * every habit or log change; training is heavier and only happens through [train]/[refreshIfStale],
 * always on [compute], never on a check-in.
 */
class ForecastRepository(
    private val data: HabitDataSource,
    private val modelJson: Flow<String?>,
    private val saveModelJson: suspend (String) -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val compute: CoroutineContext = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The stored model, or null when none is stored or it can't be read. */
    private val model: Flow<RiskModel?> = modelJson.map { text ->
        text?.let { runCatching { json.decodeFromString(RiskModel.serializer(), it) }.getOrNull() }
    }

    fun observeToday(): Flow<Forecast> =
        combine(model, data.observeAllHabits(), data.observeAllLogs()) { m, habits, logs ->
            Forecaster.today(m ?: RiskModel.starter(0L), habits, logs, today(), zone)
        }.flowOn(compute)

    suspend fun train(): RiskModel = withContext(compute) {
        val day = today()
        val rows = FeatureExtractor.rows(data.observeAllHabits().first(), data.observeAllLogs().first(), day, zone)
        RiskModel.fit(rows, day, clock())
    }.also { saveModelJson(json.encodeToString(RiskModel.serializer(), it)) }

    suspend fun refreshIfStale(maxAgeMs: Long = 86_400_000L) {
        val current = model.first()
        if (current == null || clock() - current.trainedAt > maxAgeMs) train()
    }

    /** Today's miss probability per habit id, for habits due and not yet finished. */
    suspend fun todayRisks(): Map<Long, Double> = observeToday().first().items.associate { it.habitId to it.probability }
}

object ForecastServices {
    @Volatile private var instance: ForecastRepository? = null

    fun repository(context: Context): ForecastRepository = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            val db = AppDatabase.getInstance(app)
            val prefs = AiServices.prefs(app)
            ForecastRepository(
                HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao()),
                prefs.forecastModelJson,
                prefs::setForecastModelJson,
            )
        }.also { instance = it }
    }
}
