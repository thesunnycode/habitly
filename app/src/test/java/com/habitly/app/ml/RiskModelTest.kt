package com.habitly.app.ml

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

class RiskModelTest {
    private val today = LocalDate.of(2026, 9, 30)

    private fun mondayRows(): List<TrainingRow> {
        val (habits, logs) = SyntheticHistory.mondaySkipper(today, days = 120, habits = 3, random = Random(42))
        return FeatureExtractor.rows(habits, logs, today, ZoneOffset.UTC)
    }

    @Test fun learnsMondayPatternWithGoodHeldOutAuc() {
        val model = RiskModel.fit(mondayRows(), today, now = 1L)
        assertFalse(model.isStarter)
        val metrics = requireNotNull(model.metrics)
        assertTrue("auc=${metrics.auc}", requireNotNull(metrics.auc) > 0.7)
        assertTrue("auc=${metrics.auc} baseline=${metrics.baselineAuc}", metrics.auc!! > requireNotNull(metrics.baselineAuc))
        assertTrue(metrics.brier < 0.2)
    }

    @Test fun mondayRiskAboveTuesday() {
        val rows = mondayRows()
        val model = RiskModel.fit(rows, today, now = 1L)
        val monday = rows.last { it.date.dayOfWeek == DayOfWeek.MONDAY }
        // Thursday is mid-week, so only the weekday differs from Monday.
        val thursday = rows.last { it.date.dayOfWeek == DayOfWeek.THURSDAY }
        assertTrue(model.predict(monday.x) > model.predict(thursday.x) + 0.3)
    }

    @Test fun deterministic() {
        val rows = mondayRows()
        assertEquals(RiskModel.train(rows, 1L), RiskModel.train(rows, 1L))
    }

    @Test fun tooFewRowsOrOneClassUsesStarter() {
        val rows = mondayRows()
        assertTrue(RiskModel.fit(rows.take(39), today, 1L).isStarter)
        assertTrue(RiskModel.fit(rows.filter { it.y == 0 }, today, 1L).isStarter)
    }

    @Test fun exactlyMinRowsWithBothClassesTrains() {
        val all = mondayRows()
        val rows = all.filter { it.y == 1 }.take(10) + all.filter { it.y == 0 }.take(30)
        assertEquals(RiskModel.MIN_ROWS, rows.size)
        assertFalse(RiskModel.fit(rows, today, 1L).isStarter)
    }

    @Test fun allPositiveRowsUseStarter() {
        val positives = mondayRows().filter { it.y == 1 }
        assertTrue(positives.size >= RiskModel.MIN_ROWS)
        assertTrue(RiskModel.fit(positives, today, 1L).isStarter)
    }

    @Test fun starterIsSensible() {
        val starter = RiskModel.starter(0L)
        val good = DoubleArray(FEATURE_COUNT).also { it[F_RATE7] = 1.0; it[F_RATE28] = 1.0; it[F_SAME_WEEKDAY] = 1.0; it[F_STREAK] = ln(8.0) }
        val bad = DoubleArray(FEATURE_COUNT).also { it[F_MISSED_PREV] = 1.0 }
        assertTrue(starter.predict(good) < 0.3)
        assertTrue(starter.predict(bad) > 0.6)
        val mid = DoubleArray(FEATURE_COUNT).also { it[F_RATE7] = 0.5; it[F_RATE28] = 0.5; it[F_SAME_WEEKDAY] = 0.5 }
        assertTrue(starter.predict(mid) in 0.2..0.6)
    }

    @Test fun aucHandlesTiesAndSingleClass() {
        assertEquals(1.0, RiskModel.auc(listOf(0.9, 0.1), listOf(1, 0))!!, 1e-9)
        assertEquals(0.5, RiskModel.auc(listOf(0.5, 0.5), listOf(1, 0))!!, 1e-9)
        assertNull(RiskModel.auc(listOf(0.3, 0.4), listOf(1, 1)))
    }

    @Test fun roundTripsThroughJson() {
        val model = RiskModel.fit(mondayRows(), today, 1L)
        val json = Json.encodeToString(RiskModel.serializer(), model)
        assertEquals(model, Json.decodeFromString(RiskModel.serializer(), json))
    }

    @Test fun oldJsonWithoutNewMetricFieldsStillDecodes() {
        val old = """{"accuracy":0.8,"auc":0.75,"brier":0.15,"testRows":20}"""
        val m = Json.decodeFromString(ModelMetrics.serializer(), old)
        assertEquals(ModelMetrics(0.8, 0.75, 0.15, 20), m)
        assertNull(m.baselineAuc); assertNull(m.baselineBrier); assertNull(m.lambda)
        assertEquals(1, m.folds)
    }

    @Test fun jsonKeepsTheNewMetricFields() {
        val metrics = requireNotNull(RiskModel.fit(mondayRows(), today, 1L).metrics)
        assertNotNull(metrics.lambda); assertNotNull(metrics.baselineAuc); assertNotNull(metrics.baselineBrier)
        val json = Json.encodeToString(ModelMetrics.serializer(), metrics)
        assertEquals(metrics, Json.decodeFromString(ModelMetrics.serializer(), json))
    }

    @Test fun baselineIsOneMinusTheMonthlyRate() {
        fun x(rate28: Double) = DoubleArray(FEATURE_COUNT).also { it[F_RATE28] = rate28 }
        assertEquals(0.1, RiskModel.baselinePredict(x(0.9)), 1e-12)
        assertEquals(0.02, RiskModel.baselinePredict(x(1.0)), 1e-12)
        assertEquals(0.98, RiskModel.baselinePredict(x(0.0)), 1e-12)
    }

    @Test fun foldRangesAreTheLastWeeksNewestFirst() {
        val ranges = RiskModel.foldRanges(today, 3)
        assertEquals(
            listOf(today.minusDays(7) to today, today.minusDays(14) to today.minusDays(7), today.minusDays(21) to today.minusDays(14)),
            ranges,
        )
    }

    @Test fun foldsNeverTrainOnRowsOnOrAfterTheirTestWindow() {
        val rows = mondayRows()
        val splits = RiskModel.foldSplits(rows, today, 4)
        assertEquals(4, splits.size)
        for ((k, split) in splits.withIndex()) {
            val (from, until) = RiskModel.foldRanges(today, 4)[k]
            val (train, test) = split
            assertTrue(train.all { it.date.isBefore(from) })
            assertTrue(test.isNotEmpty() && test.all { !it.date.isBefore(from) && it.date.isBefore(until) })
            assertEquals(rows.count { it.date.isBefore(from) }, train.size)
        }
    }

    @Test fun unusableFoldsAreSkipped() {
        val rows = mondayRows()
        // Only the last 16 days: folds 1-2 train on 9 or 2 days' rows (< MIN_ROWS), fold 3 has no train rows, fold 4 no test rows.
        val recent = rows.filter { !it.date.isBefore(today.minusDays(16)) }
        assertTrue(recent.size >= RiskModel.MIN_ROWS)
        assertNull(RiskModel.crossValidate(recent, today, 0.01))
        val model = RiskModel.fit(recent, today, 1L)
        assertFalse(model.isStarter)
        assertNull(model.metrics)
    }

    @Test fun crossValidatePoolsOutOfFoldPredictions() {
        val rows = mondayRows()
        val (p, y) = requireNotNull(RiskModel.crossValidate(rows, today, 0.01))
        val splits = RiskModel.foldSplits(rows, today, 4)
        val test = splits.flatMap { it.second }
        assertEquals(test.map { it.y }, y)
        val expected = splits.flatMap { (train, t) -> RiskModel.train(train, 0L, 0.01).let { m -> t.map { m.predict(it.x) } } }
        assertEquals(expected, p)
    }

    private fun brier(p: List<Double>, y: List<Int>) = p.indices.sumOf { (p[it] - y[it]).let { d -> d * d } } / p.size

    /** Labels are coin flips, so no feature helps and heavier regularisation should win. */
    private fun noiseRows(): List<TrainingRow> {
        val random = Random(3)
        return (0 until 300).map { n ->
            val date = today.minusDays(1L + n / 4)
            TrainingRow(n % 4L, date, DoubleArray(FEATURE_COUNT) { random.nextDouble() }, if (random.nextBoolean()) 1 else 0)
        }
    }

    @Test fun lambdaIsTheMinimumBrierValue() {
        for (rows in listOf(mondayRows(), noiseRows())) {
            var best = Double.MAX_VALUE
            var chosen = Double.NaN
            for (lambda in RiskModel.LAMBDAS) {
                val (p, y) = requireNotNull(RiskModel.crossValidate(rows, today, lambda))
                val b = brier(p, y)
                if (b <= best) { best = b; chosen = lambda }
            }
            val metrics = requireNotNull(RiskModel.fit(rows, today, 1L).metrics)
            assertEquals(chosen, requireNotNull(metrics.lambda), 0.0)
            assertEquals(best, metrics.brier, 1e-12)
        }
        assertEquals(0.3, RiskModel.fit(noiseRows(), today, 1L).metrics!!.lambda!!, 0.0)
    }

    @Test fun metricsCarryTheBaselineOnTheSamePooledRows() {
        val rows = mondayRows()
        val metrics = requireNotNull(RiskModel.fit(rows, today, 1L).metrics)
        val test = RiskModel.foldSplits(rows, today, 4).flatMap { it.second }
        val baseline = test.map { RiskModel.baselinePredict(it.x) }
        val labels = test.map { it.y }
        assertEquals(4, metrics.folds)
        assertEquals(test.size, metrics.testRows)
        assertEquals(RiskModel.auc(baseline, labels)!!, metrics.baselineAuc!!, 1e-12)
        assertEquals(brier(baseline, labels), metrics.baselineBrier!!, 1e-12)
    }

    @Test fun trainingRunsToConvergence() {
        val rows = mondayRows()
        val lambda = 0.01
        val model = RiskModel.train(rows, 1L, lambda)
        val gw = DoubleArray(FEATURE_COUNT)
        var gb = 0.0
        for (r in rows) {
            val err = model.predict(r.x) - r.y
            for (i in 0 until FEATURE_COUNT) gw[i] += err * model.standardized(r.x, i)
            gb += err
        }
        val maxGrad = maxOf(abs(gb / rows.size), (0 until FEATURE_COUNT).maxOf { abs(gw[it] / rows.size + lambda * model.weights[it]) })
        assertTrue("max gradient $maxGrad", maxGrad < 1e-5)
    }
}
