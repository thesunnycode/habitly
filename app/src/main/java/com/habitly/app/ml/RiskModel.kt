package com.habitly.app.ml

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

@Serializable
data class ModelMetrics(
    val accuracy: Double,
    val auc: Double?,
    val brier: Double,
    val testRows: Int,
    val baselineAuc: Double? = null,
    val baselineBrier: Double? = null,
    val folds: Int = 1,
    val lambda: Double? = null,
)

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
        internal val LAMBDAS = listOf(0.001, 0.003, 0.01, 0.03, 0.1, 0.3)
        private const val DEFAULT_LAMBDA = 0.01
        private const val FOLDS = 4

        private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

        /**
         * Hand-set model used until there is enough personal history (>= 40 rows, both classes).
         * Reference means/stds are neutral typical values so explanations read the right way round
         * (a patchy week is below the mean, a strong one above). The weights are the raw-space
         * coefficients below scaled by each std, with the bias absorbing the means.
         */
        fun starter(now: Long): RiskModel {
            val means = DoubleArray(FEATURE_COUNT)
            val stds = DoubleArray(FEATURE_COUNT) { 1.0 }
            for (i in 0..6) { means[i] = 1.0 / 7; stds[i] = 0.35 }
            means[F_RATE7] = 0.5; stds[F_RATE7] = 0.25
            means[F_RATE28] = 0.5; stds[F_RATE28] = 0.25
            means[F_SAME_WEEKDAY] = 0.5; stds[F_SAME_WEEKDAY] = 0.25
            means[F_STREAK] = ln(4.0); stds[F_STREAK] = 0.8
            means[F_MISSED_PREV] = 0.3; stds[F_MISSED_PREV] = 0.46
            means[F_AGE] = ln(31.0); stds[F_AGE] = 1.0
            means[F_REMINDER] = 0.5; stds[F_REMINDER] = 0.5
            means[F_WEEKEND] = 2.0 / 7; stds[F_WEEKEND] = sqrt(2.0 / 7 * 5.0 / 7)
            means[F_MOMENTUM] = 0.5; stds[F_MOMENTUM] = 0.3
            means[F_SINCE_KEPT] = ln(2.0); stds[F_SINCE_KEPT] = 0.7
            means[F_TREND] = 0.0; stds[F_TREND] = 0.2
            means[F_RATE90] = 0.5; stds[F_RATE90] = 0.25
            means[F_RESTED_PREV] = 0.05; stds[F_RESTED_PREV] = 0.22
            val raw = DoubleArray(FEATURE_COUNT)
            raw[F_RATE7] = -2.5; raw[F_RATE28] = -1.0; raw[F_SAME_WEEKDAY] = -1.0; raw[F_STREAK] = -0.4; raw[F_MISSED_PREV] = 1.2
            raw[F_SINCE_KEPT] = 0.4; raw[F_MOMENTUM] = -0.5
            val weights = List(FEATURE_COUNT) { raw[it] * stds[it] }
            val bias = 1.6 + (0 until FEATURE_COUNT).sumOf { raw[it] * means[it] }
            return RiskModel(weights, bias, means.toList(), stds.toList(), trainedAt = now, rows = 0, isStarter = true)
        }

        /**
         * L2-regularised batch gradient descent on z-scored features, run until the largest gradient
         * component is below [tolerance] or [maxEpochs] is reached. Deterministic (no shuffling).
         */
        fun train(
            rows: List<TrainingRow>,
            now: Long,
            lambda: Double = DEFAULT_LAMBDA,
            maxEpochs: Int = 3000,
            lr: Double = 0.2,
            tolerance: Double = 1e-5,
        ): RiskModel {
            val m = rows.size
            val n = FEATURE_COUNT
            val means = DoubleArray(n)
            for (r in rows) for (i in 0 until n) means[i] += r.x[i]
            for (i in 0 until n) means[i] /= m
            val stds = DoubleArray(n)
            for (r in rows) for (i in 0 until n) { val d = r.x[i] - means[i]; stds[i] += d * d }
            for (i in 0 until n) { val s = sqrt(stds[i] / m); stds[i] = if (s < 1e-9) 1.0 else s }
            // Row-major z-scores in one primitive array keep the inner loop allocation-free.
            val z = DoubleArray(m * n)
            val y = DoubleArray(m)
            for (k in 0 until m) {
                val x = rows[k].x
                for (i in 0 until n) z[k * n + i] = (x[i] - means[i]) / stds[i]
                y[k] = rows[k].y.toDouble()
            }
            val w = DoubleArray(n)
            val gw = DoubleArray(n)
            var b = 0.0
            for (epoch in 0 until maxEpochs) {
                gw.fill(0.0)
                var gb = 0.0
                for (k in 0 until m) {
                    val o = k * n
                    var s = b
                    for (i in 0 until n) s += w[i] * z[o + i]
                    val err = sigmoid(s) - y[k]
                    for (i in 0 until n) gw[i] += err * z[o + i]
                    gb += err
                }
                gb /= m
                var maxGrad = abs(gb)
                for (i in 0 until n) {
                    gw[i] = gw[i] / m + lambda * w[i]
                    maxGrad = maxOf(maxGrad, abs(gw[i]))
                }
                if (maxGrad < tolerance) break
                for (i in 0 until n) w[i] -= lr * gw[i]
                b -= lr * gb
            }
            return RiskModel(w.toList(), b, means.toList(), stds.toList(), now, m)
        }

        /** "You usually keep it N% of the time": the miss rate over the last 28 due days. */
        fun baselinePredict(x: DoubleArray): Double = (1.0 - x[F_RATE28]).coerceIn(0.02, 0.98)

        /** Test windows `[today - 7k, today - 7(k-1))` for k = 1..[folds], newest first. */
        internal fun foldRanges(today: LocalDate, folds: Int): List<Pair<LocalDate, LocalDate>> =
            (1..folds).map { k -> today.minusDays(7L * k) to today.minusDays(7L * (k - 1)) }

        /** Usable (train, test) splits, newest first; train is every row dated before the test window. */
        internal fun foldSplits(rows: List<TrainingRow>, today: LocalDate, folds: Int): List<Pair<List<TrainingRow>, List<TrainingRow>>> =
            foldRanges(today, folds).mapNotNull { (from, until) ->
                val train = rows.filter { it.date.isBefore(from) }
                val test = rows.filter { !it.date.isBefore(from) && it.date.isBefore(until) }
                val usable = train.size >= MIN_ROWS && train.any { it.y == 1 } && train.any { it.y == 0 } && test.isNotEmpty()
                if (usable) train to test else null
            }

        private class Pooled(val predictions: List<Double>, val labels: List<Int>, val test: List<TrainingRow>, val folds: Int)

        private fun pooled(splits: List<Pair<List<TrainingRow>, List<TrainingRow>>>, lambda: Double): Pooled? {
            if (splits.isEmpty()) return null
            val predictions = ArrayList<Double>()
            for ((train, test) in splits) {
                val model = train(train, 0L, lambda)
                test.mapTo(predictions) { model.predict(it.x) }
            }
            val test = splits.flatMap { it.second }
            return Pooled(predictions, test.map { it.y }, test, splits.size)
        }

        /**
         * Rolling-origin cross-validation over the last [folds] weeks: each week is predicted by a model
         * trained only on rows dated before it. Returns pooled out-of-fold predictions and labels, or
         * null when no fold had enough training rows of both classes and a non-empty test week.
         */
        fun crossValidate(rows: List<TrainingRow>, today: LocalDate, lambda: Double, folds: Int = FOLDS): Pair<List<Double>, List<Int>>? =
            pooled(foldSplits(rows, today, folds), lambda)?.let { it.predictions to it.labels }

        /**
         * Picks lambda by rolling-origin cross-validation (lowest pooled Brier score, ties to the larger
         * value), reports those out-of-fold metrics next to the "usual rate" baseline, then retrains on
         * every row for use.
         */
        fun fit(rows: List<TrainingRow>, today: LocalDate, now: Long): RiskModel {
            if (rows.size < MIN_ROWS || rows.none { it.y == 1 } || rows.none { it.y == 0 }) return starter(now)
            val splits = foldSplits(rows, today, FOLDS)
            var bestLambda = DEFAULT_LAMBDA
            var best: Pooled? = null
            var bestBrier = Double.MAX_VALUE
            for (lambda in LAMBDAS) {
                val cv = pooled(splits, lambda) ?: break // no usable fold: the same for every lambda
                val b = brier(cv.predictions, cv.labels)
                if (b <= bestBrier) { bestBrier = b; bestLambda = lambda; best = cv }
            }
            val metrics = best?.let { cv ->
                val baseline = cv.test.map { baselinePredict(it.x) }
                ModelMetrics(
                    accuracy = accuracy(cv.predictions, cv.labels),
                    auc = auc(cv.predictions, cv.labels),
                    brier = bestBrier,
                    testRows = cv.test.size,
                    baselineAuc = auc(baseline, cv.labels),
                    baselineBrier = brier(baseline, cv.labels),
                    folds = cv.folds,
                    lambda = bestLambda,
                )
            }
            return train(rows, now, bestLambda).copy(metrics = metrics)
        }

        fun evaluate(model: RiskModel, test: List<TrainingRow>): ModelMetrics {
            val p = test.map { model.predict(it.x) }
            val y = test.map { it.y }
            return ModelMetrics(accuracy(p, y), auc(p, y), brier(p, y), test.size)
        }

        private fun accuracy(p: List<Double>, y: List<Int>) = p.indices.count { (p[it] >= 0.5) == (y[it] == 1) }.toDouble() / p.size

        private fun brier(p: List<Double>, y: List<Int>) = p.indices.sumOf { (p[it] - y[it]).let { d -> d * d } } / p.size

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
