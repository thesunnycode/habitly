package com.habitly.app.ml

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Realistic benchmark for the miss-risk model (Task 6b). The oracle scores each pooled out-of-fold
 * test row with the generator's true miss probability, i.e. the best any model could do.
 *
 * The features were chosen while looking at seeds 1..20, so those numbers are in-sample. The
 * across-seed assertions run only on the held-out seeds 101..120, which were not looked at until
 * the model code was frozen. The generator, seeds and thresholds are fixed; measured numbers go to
 * the test report's system-out.
 */
class ForecastBenchmarkTest {
    private val today = LocalDate.of(2026, 9, 30)

    private class SeedResult(
        val seed: Int, val rows: Int, val metrics: ModelMetrics, val fixedLambdaBrier: Double,
        val oracleAuc: Double, val oracleBrier: Double, val fitMillis: Long,
    )

    private class Summary(val results: List<SeedResult>, val totalMillis: Long) {
        val auc = results.map { it.metrics.auc!! }.average()
        val oracleAuc = results.map { it.oracleAuc }.average()
        val ratio = auc / oracleAuc
        val baseAuc = results.map { it.metrics.baselineAuc!! }.average()
        val brier = results.map { it.metrics.brier }.average()
        val fixedLambdaBrier = results.map { it.fixedLambdaBrier }.average()
        val baseBrier = results.map { it.metrics.baselineBrier!! }.average()
        val oracleBrier = results.map { it.oracleBrier }.average()
        val wins = results.count { it.metrics.auc!! > it.metrics.baselineAuc!! }
        val brierWins = results.count { it.metrics.brier < it.metrics.baselineBrier!! }
        val lambdas = results.groupingBy { it.metrics.lambda!! }.eachCount().toSortedMap()
        override fun toString() = ("MEAN seeds=${results.first().seed}..${results.last().seed} auc=%.4f oracleAuc=%.4f ratio=%.4f " +
            "baseAuc=%.4f brier=%.4f fixedLambda0.01Brier=%.4f baseBrier=%.4f oracleBrier=%.4f oracleBrierGap=%.4f ").format(
            auc, oracleAuc, ratio, baseAuc, brier, fixedLambdaBrier, baseBrier, oracleBrier, brier - oracleBrier,
        ) + "aucWins=$wins/${results.size} brierWins=$brierWins/${results.size} lambdas=$lambdas totalMs=$totalMillis"
    }

    private companion object {
        val HELD_OUT_SEEDS = 101..120

        /** Computed once for the across-seed assertions. */
        val heldOut: Summary by lazy {
            val bench = ForecastBenchmarkTest()
            val start = System.nanoTime()
            val results = HELD_OUT_SEEDS.map { bench.run(it) }
            Summary(results, (System.nanoTime() - start) / 1_000_000).also { summary ->
                println(results.joinToString(System.lineSeparator()) { with(bench) { it.line() } } + System.lineSeparator() + summary)
            }
        }
    }

    private fun brier(p: List<Double>, y: List<Int>) = p.indices.sumOf { (p[it] - y[it]).let { d -> d * d } } / p.size

    private fun run(seed: Int): SeedResult {
        val data = RealisticHistory.generate(today, random = Random(seed))
        val rows = FeatureExtractor.rows(data.habits, data.logs, today, ZoneOffset.UTC)
        val start = System.nanoTime()
        val model = RiskModel.fit(rows, today, 1L)
        val millis = (System.nanoTime() - start) / 1_000_000
        // The same pooled out-of-fold rows the metrics were measured on.
        val test = RiskModel.foldSplits(rows, today, 4).flatMap { it.second }
        val oracle = test.map { 1.0 - data.trueKeepProb.getValue(it.habitId to it.date) }
        val labels = test.map { it.y }
        // λ fixed in advance, so the optimism from choosing λ on the same folds is visible.
        val (fixed, fixedLabels) = requireNotNull(RiskModel.crossValidate(rows, today, 0.01))
        return SeedResult(
            seed, rows.size, requireNotNull(model.metrics), brier(fixed, fixedLabels),
            RiskModel.auc(oracle, labels)!!, brier(oracle, labels), millis,
        )
    }

    private fun SeedResult.line() = "seed=$seed rows=$rows folds=${metrics.folds} lambda=${metrics.lambda} testRows=${metrics.testRows} " +
        ("auc=%.3f acc=%.3f brier=%.4f fixedLambdaBrier=%.4f baseAuc=%.3f baseBrier=%.4f oracleAuc=%.3f oracleBrier=%.4f " +
            "fitMs=$fitMillis").format(
            metrics.auc, metrics.accuracy, metrics.brier, fixedLambdaBrier, metrics.baselineAuc, metrics.baselineBrier, oracleAuc, oracleBrier,
        )

    @Test fun seedSevenBeatsTheUsualRateBaseline() {
        val r = run(7)
        val m = r.metrics
        val summary = r.line()
        println(summary)
        assertTrue(summary, m.folds >= 3)
        assertTrue(summary, m.auc!! > m.baselineAuc!! + 0.05)
        assertTrue(summary, m.brier < m.baselineBrier!!)
    }

    @Test fun heldOutSeedsBeatTheBaselineAucOnAtLeastSeventeen() {
        val s = heldOut
        assertTrue("$s", s.wins >= 17)
    }

    @Test fun heldOutSeedsMeanBrierBelowTheBaseline() {
        val s = heldOut
        assertTrue("$s", s.brier < s.baseBrier)
    }

    /**
     * Regression guard set after measurement (in-sample ratio 0.919 on seeds 1..20), not an accuracy
     * claim: it catches a change that makes the model clearly worse relative to the oracle.
     */
    @Test fun regressionGuardHeldOutAucRatioToOracleAtLeast088() {
        val s = heldOut
        assertTrue("$s", s.ratio >= 0.88)
    }

    /** The realistic history gives about 585 rows (150 days x 4 habits): the size of a long on-phone history. */
    @Test fun fullFitTiming() {
        val cold = run(7)
        val warm = run(7)
        println("timing " + cold.line() + " warmFitMs=${warm.fitMillis}")
        assertTrue(cold.line(), cold.fitMillis < 5000)
    }
}
