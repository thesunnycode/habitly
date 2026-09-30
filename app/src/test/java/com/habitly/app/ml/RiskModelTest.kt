package com.habitly.app.ml

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
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
}
