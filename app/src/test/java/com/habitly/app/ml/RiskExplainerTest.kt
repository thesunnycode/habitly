package com.habitly.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.random.Random

class RiskExplainerTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val date = LocalDate.of(2026, 9, 28) // Monday

    private fun model(
        weights: Map<Int, Double>,
        means: Map<Int, Double> = emptyMap(),
        bias: Double = 0.0,
    ) = RiskModel(
        weights = List(FEATURE_COUNT) { weights[it] ?: 0.0 },
        bias = bias,
        means = List(FEATURE_COUNT) { means[it] ?: 0.0 },
        stds = List(FEATURE_COUNT) { 1.0 },
        trainedAt = 0L, rows = 100,
    )

    private fun x(vararg set: Pair<Int, Double>) = DoubleArray(FEATURE_COUNT).also { a -> set.forEach { (i, v) -> a[i] = v } }

    @Test fun mondayModelSaysYouOftenMissMondays() {
        val (habits, logs) = SyntheticHistory.mondaySkipper(today, days = 120, habits = 3, random = Random(42))
        val rows = FeatureExtractor.rows(habits, logs, today, ZoneOffset.UTC)
        val model = RiskModel.fit(rows, today, now = 1L)
        val monday = rows.last { it.date.dayOfWeek == java.time.DayOfWeek.MONDAY }
        assertEquals("you often miss Mondays", RiskExplainer.reason(model, monday.x, monday.date))
    }

    @Test fun missedPrevious() {
        val m = model(mapOf(F_MISSED_PREV to 1.2))
        assertEquals("missed last time", RiskExplainer.reason(m, x(F_MISSED_PREV to 1.0), date))
    }

    @Test fun streak() {
        val m = model(mapOf(F_STREAK to -1.0))
        assertEquals("on a 5-day roll", RiskExplainer.reason(m, x(F_STREAK to ln(6.0)), date))
    }

    @Test fun patchyWeek() {
        val m = model(mapOf(F_RATE7 to -2.0), means = mapOf(F_RATE7 to 0.8))
        assertEquals("a patchy last week", RiskExplainer.reason(m, x(F_RATE7 to 0.2), date))
    }

    @Test fun noReminder() {
        val m = model(mapOf(F_REMINDER to -1.0), means = mapOf(F_REMINDER to 0.5))
        assertEquals("no reminder set", RiskExplainer.reason(m, x(F_REMINDER to 0.0), date))
    }

    @Test fun allZeroWeightsIsOnTrack() {
        assertEquals("you're on track", RiskExplainer.reason(model(emptyMap()), x(), date))
    }

    @Test fun neverNamesAnotherWeekday() {
        val m = model(mapOf(1 to -3.0), means = mapOf(1 to 0.14))
        val wednesday = LocalDate.of(2026, 9, 30)
        val reason = RiskExplainer.reason(m, x(2 to 1.0), wednesday)
        assertFalse(reason, reason.contains("Tuesday"))
        assertEquals("you're on track", reason)
    }
}
