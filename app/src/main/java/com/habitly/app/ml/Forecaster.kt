package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class RiskLevel(val label: String) {
    HIGH("High"), MEDIUM("Medium"), LOW("Low");

    companion object {
        fun of(p: Double): RiskLevel = when {
            p >= 0.6 -> HIGH
            p >= 0.3 -> MEDIUM
            else -> LOW
        }
    }
}

data class HabitRisk(
    val habitId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val probability: Double,
    val level: RiskLevel,
    val reason: String,
) {
    val percent: Int get() = (probability * 100).roundToInt()
}

data class Forecast(
    val items: List<HabitRisk>,
    val isLearning: Boolean,
    val dueToday: Int,
    val trainedAt: Long,
    val rows: Int,
    val metrics: ModelMetrics?,
) {
    val allClear: Boolean get() = items.isEmpty() && dueToday > 0
}

object Forecaster {
    /** Risk of missing each habit due [today] and not yet done or rested, riskiest first. */
    fun today(
        model: RiskModel,
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Forecast {
        val histories = FeatureExtractor.histories(habits.filter { !it.isArchived }, logs, zone)
        // The same cross-habit totals training uses, so today's momentum feature matches the model's rows.
        val totals = DayTotals.of(histories, today)
        var due = 0
        val items = histories.mapNotNull { h ->
            if (today.isBefore(h.start) || !h.schedule.isDue(today)) return@mapNotNull null
            due++
            val status = h.statuses[today]
            if (status == CompletionStatus.DONE || status == CompletionStatus.SKIPPED) return@mapNotNull null
            val x = FeatureExtractor.features(h, today, totals)
            val p = model.predict(x)
            HabitRisk(h.habit.id, h.habit.name, h.habit.icon, h.habit.colorHex, p, RiskLevel.of(p), RiskExplainer.reason(model, x, today))
        }
        return Forecast(items.sortedByDescending { it.probability }, model.isStarter, due, model.trainedAt, model.rows, model.metrics)
    }
}
