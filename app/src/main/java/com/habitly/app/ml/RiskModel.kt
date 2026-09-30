package com.habitly.app.ml

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.sqrt

@Serializable
data class ModelMetrics(val accuracy: Double, val auc: Double?, val brier: Double, val testRows: Int)

@Serializable
data class RiskModel(
    val weights: List<Double>,
    val bias: Double,
    val means: List<Double>,
    val stds: List<Double>,
    val trainedAt: Long,
    val rows: Int,
    val metrics: ModelMetrics? = null,
    val isStarter: Boolean = false,
) {
    fun standardized(x: DoubleArray, i: Int): Double = (x[i] - means[i]) / stds[i]

    fun contributions(x: DoubleArray): DoubleArray = DoubleArray(FEATURE_COUNT) { i -> weights[i] * standardized(x, i) }

    fun predict(x: DoubleArray): Double = sigmoid(bias + contributions(x).sum())

    companion object {
        const val MIN_ROWS = 40
        private const val HOLDOUT_DAYS = 7L

        private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

        /** Hand-set weights used until there is enough personal history (>= 40 rows, both classes). */
        fun starter(now: Long): RiskModel {
            val w = DoubleArray(FEATURE_COUNT)
            w[F_RATE7] = -2.5; w[F_RATE28] = -1.0; w[F_SAME_WEEKDAY] = -1.0; w[F_STREAK] = -0.4; w[F_MISSED_PREV] = 1.2
            return RiskModel(w.toList(), bias = 1.6, means = List(FEATURE_COUNT) { 0.0 }, stds = List(FEATURE_COUNT) { 1.0 },
                trainedAt = now, rows = 0, isStarter = true)
        }

        /** L2-regularised batch gradient descent on z-scored features. Deterministic. */
        fun train(rows: List<TrainingRow>, now: Long, lambda: Double = 0.01, epochs: Int = 400, lr: Double = 0.1): RiskModel {
            val m = rows.size
            val means = DoubleArray(FEATURE_COUNT) { i -> rows.sumOf { it.x[i] } / m }
            val stds = DoubleArray(FEATURE_COUNT) { i ->
                val s = sqrt(rows.sumOf { (it.x[i] - means[i]).let { d -> d * d } } / m)
                if (s < 1e-9) 1.0 else s
            }
            val z = rows.map { r -> DoubleArray(FEATURE_COUNT) { i -> (r.x[i] - means[i]) / stds[i] } }
            val w = DoubleArray(FEATURE_COUNT)
            var b = 0.0
            repeat(epochs) {
                val gw = DoubleArray(FEATURE_COUNT)
                var gb = 0.0
                for (k in 0 until m) {
                    var s = b
                    for (i in 0 until FEATURE_COUNT) s += w[i] * z[k][i]
                    val err = sigmoid(s) - rows[k].y
                    for (i in 0 until FEATURE_COUNT) gw[i] += err * z[k][i]
                    gb += err
                }
                for (i in 0 until FEATURE_COUNT) w[i] -= lr * (gw[i] / m + lambda * w[i])
                b -= lr * gb / m
            }
            return RiskModel(w.toList(), b, means.toList(), stds.toList(), now, m)
        }

        /** Holds out the last 7 days to measure, then retrains on everything for use. */
        fun fit(rows: List<TrainingRow>, today: LocalDate, now: Long): RiskModel {
            if (rows.size < MIN_ROWS || rows.none { it.y == 1 } || rows.none { it.y == 0 }) return starter(now)
            val cutoff = today.minusDays(HOLDOUT_DAYS)
            val (test, trainRows) = rows.partition { !it.date.isBefore(cutoff) }
            val metrics = if (test.isNotEmpty() && trainRows.any { it.y == 1 } && trainRows.any { it.y == 0 }) {
                evaluate(train(trainRows, now), test)
            } else null
            return train(rows, now).copy(metrics = metrics)
        }

        fun evaluate(model: RiskModel, test: List<TrainingRow>): ModelMetrics {
            val p = test.map { model.predict(it.x) }
            val y = test.map { it.y }
            val accuracy = p.indices.count { (p[it] >= 0.5) == (y[it] == 1) }.toDouble() / test.size
            val brier = p.indices.sumOf { (p[it] - y[it]).let { d -> d * d } } / test.size
            return ModelMetrics(accuracy, auc(p, y), brier, test.size)
        }

        /** Mann-Whitney AUC with average ranks for ties; null when a class is missing. */
        fun auc(scores: List<Double>, labels: List<Int>): Double? {
            val pos = labels.count { it == 1 }
            val neg = labels.size - pos
            if (pos == 0 || neg == 0) return null
            val order = scores.indices.sortedBy { scores[it] }
            val ranks = DoubleArray(scores.size)
            var i = 0
            while (i < order.size) {
                var j = i
                while (j + 1 < order.size && scores[order[j + 1]] == scores[order[i]]) j++
                val avg = (i + j) / 2.0 + 1.0
                for (k in i..j) ranks[order[k]] = avg
                i = j + 1
            }
            val sumPos = labels.indices.filter { labels[it] == 1 }.sumOf { ranks[it] }
            return (sumPos - pos * (pos + 1) / 2.0) / (pos.toDouble() * neg)
        }
    }
}
