package com.habitly.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // Starter vectors give the Task 6b features their neutral values (other habits half kept lately).
    private val patchyReasons = setOf("a patchy last week", "it's slipped this month", "no streak going yet")

    @Test fun starterPatchyWeekIsARiskReason() {
        val starter = RiskModel.starter(0L)
        val patchy = x(F_RATE7 to 0.29, F_RATE28 to 0.3, F_SAME_WEEKDAY to 0.25, F_STREAK to ln(4.0), F_MOMENTUM to 0.5, F_SINCE_KEPT to ln(3.0))
        assertEquals("a patchy last week", RiskExplainer.reason(starter, patchy, date))
        val noStreak = x(F_RATE7 to 0.29, F_RATE28 to 0.3, F_SAME_WEEKDAY to 0.25, F_MOMENTUM to 0.5, F_SINCE_KEPT to ln(3.0))
        val reason = RiskExplainer.reason(starter, noStreak, date)
        assertFalse(reason, reason == "a strong last week")
        assertTrue(reason, reason in patchyReasons)
    }

    @Test fun starterStrongWeekIsPositive() {
        val starter = RiskModel.starter(0L)
        val strong = x(F_RATE7 to 0.95, F_RATE28 to 0.95, F_SAME_WEEKDAY to 0.95, F_STREAK to ln(8.0), F_MOMENTUM to 0.5, F_SINCE_KEPT to ln(2.0))
        val reason = RiskExplainer.reason(starter, strong, date)
        assertTrue(reason, reason == "on a 7-day roll" || reason == "a strong last week")
    }

    @Test fun strongMonthWording() {
        val m = model(mapOf(F_RATE28 to -2.0), means = mapOf(F_RATE28 to 0.5))
        assertEquals("a strong month", RiskExplainer.reason(m, x(F_RATE28 to 0.9), date))
    }

    @Test fun toughFewDays() {
        val m = model(mapOf(F_MOMENTUM to -1.0), means = mapOf(F_MOMENTUM to 0.5))
        assertEquals("the last few days have been tough", RiskExplainer.reason(m, x(F_MOMENTUM to 0.0), date))
    }

    @Test fun goodRunLately() {
        val m = model(mapOf(F_MOMENTUM to -1.0), means = mapOf(F_MOMENTUM to 0.5))
        assertEquals("you're on a good run lately", RiskExplainer.reason(m, x(F_MOMENTUM to 1.0), date))
    }

    @Test fun daysSinceLastKept() {
        val m = model(mapOf(F_SINCE_KEPT to 1.0), means = mapOf(F_SINCE_KEPT to ln(3.0)))
        assertEquals("it's been 4 days since you last did it", RiskExplainer.reason(m, x(F_SINCE_KEPT to ln(5.0)), date))
        // One day since is "yesterday", never "1 days".
        val low = model(mapOf(F_SINCE_KEPT to 1.0), means = mapOf(F_SINCE_KEPT to 0.0))
        assertEquals("you're on track", RiskExplainer.reason(low, x(F_SINCE_KEPT to ln(2.0)), date))
    }

    @Test fun slippingAndPickingUp() {
        val m = model(mapOf(F_TREND to -1.0))
        assertEquals("it's been slipping lately", RiskExplainer.reason(m, x(F_TREND to -0.4), date))
        assertEquals("you're picking it back up", RiskExplainer.reason(m, x(F_TREND to 0.4), date))
    }

    @Test fun starterReadsTheOtherHabitsLastFewDays() {
        val starter = RiskModel.starter(0L)
        val neutral = arrayOf(F_RATE7 to 0.5, F_RATE28 to 0.5, F_SAME_WEEKDAY to 0.5, F_STREAK to ln(4.0), F_SINCE_KEPT to ln(3.0))
        assertEquals("the last few days have been tough", RiskExplainer.reason(starter, x(*neutral, F_MOMENTUM to 0.0), date))
        assertEquals("you're on a good run lately", RiskExplainer.reason(starter, x(*neutral, F_MOMENTUM to 1.0), date))
    }

    @Test fun longRunRate() {
        val m = model(mapOf(F_RATE90 to -1.0), means = mapOf(F_RATE90 to 0.6))
        assertEquals("it's been hard to keep up for a while", RiskExplainer.reason(m, x(F_RATE90 to 0.3), date))
        assertEquals("a strong few months", RiskExplainer.reason(m, x(F_RATE90 to 0.9), date))
    }

    @Test fun restedLastTime() {
        val m = model(mapOf(F_RESTED_PREV to 1.0), means = mapOf(F_RESTED_PREV to 0.05))
        assertEquals("coming back from a rest day", RiskExplainer.reason(m, x(F_RESTED_PREV to 1.0), date))
    }
}
