package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class ForecasterTest {
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private val zone = ZoneOffset.UTC
    private val starter = RiskModel.starter(0L)

    private fun habit(
        id: Long, createdDaysAgo: Int = 30, type: FrequencyType = FrequencyType.DAILY,
        days: String? = null, archived: Boolean = false,
    ) = HabitEntity(
        id = id, name = "H$id", icon = "star", colorHex = "#FF922B", category = "Health",
        frequencyType = type, frequencyDays = days, intervalDays = null, reminderTime = null,
        createdAt = today.minusDays(createdDaysAgo.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
        isArchived = archived,
    )

    private fun log(habitId: Long, date: LocalDate, status: CompletionStatus) = HabitLogEntity(
        habitId = habitId, date = date.toString(), status = status, completedAt = null, note = null,
    )

    /** [daysAgo] 1..n all logged with [status]. */
    private fun run(habitId: Long, days: IntRange, status: CompletionStatus) =
        days.map { log(habitId, today.minusDays(it.toLong()), status) }

    private fun forecast(habits: List<HabitEntity>, logs: List<HabitLogEntity>, model: RiskModel = starter) =
        Forecaster.today(model, habits, logs, today, zone)

    @Test fun onlyDueUnfinishedHabitsAppearSortedByRisk() {
        val habits = listOf(
            habit(1), // kept every day: low risk
            habit(2), // missed every day: high risk
            habit(3, type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY"), // not due on Wednesday
            habit(4), // done today
            habit(5), // rested today
        )
        val logs = run(1, 1..20, CompletionStatus.DONE) + run(2, 1..20, CompletionStatus.MISSED) +
            listOf(log(4, today, CompletionStatus.DONE), log(5, today, CompletionStatus.SKIPPED))
        val f = forecast(habits, logs)
        assertEquals(listOf(2L, 1L), f.items.map { it.habitId })
        assertTrue(f.items[0].probability > f.items[1].probability)
        assertEquals(4, f.dueToday)
        assertFalse(f.allClear)
    }

    @Test fun allDueDoneIsAllClearAndNothingDueIsNot() {
        val done = forecast(listOf(habit(1), habit(2)), listOf(log(1, today, CompletionStatus.DONE), log(2, today, CompletionStatus.DONE)))
        assertTrue(done.items.isEmpty())
        assertTrue(done.allClear)

        val none = forecast(listOf(habit(1, type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY")), emptyList())
        assertEquals(0, none.dueToday)
        assertFalse(none.allClear)
    }

    @Test fun learningFollowsTheStarterFlag() {
        assertTrue(forecast(listOf(habit(1)), emptyList()).isLearning)
        val (habits, logs) = SyntheticHistory.mondaySkipper(today, 120, 3, Random(42))
        val trained = RiskModel.fit(FeatureExtractor.rows(habits, logs, today, zone), today, 1L)
        assertFalse(trained.isStarter)
        val f = forecast(habits, logs, trained)
        assertFalse(f.isLearning)
        assertEquals(trained.rows, f.rows)
        assertEquals(trained.metrics, f.metrics)
    }

    @Test fun percentRoundsAndLevelsFollowThresholds() {
        fun risk(p: Double) = HabitRisk(1, "H", "star", "#FF922B", p, RiskLevel.of(p), "r")
        assertEquals(46, risk(0.456).percent)
        // A forecast is never shown as a certainty either way.
        assertEquals(1, risk(0.004).percent)
        assertEquals(99, risk(0.9996).percent)
        assertEquals(99, risk(1.0).percent)
        assertEquals(1, risk(0.0).percent)
        assertEquals(RiskLevel.HIGH, RiskLevel.of(0.6))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(0.59))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(0.3))
        assertEquals(RiskLevel.LOW, RiskLevel.of(0.29))
    }

    @Test fun archivedAndNotYetStartedHabitsNeverAppear() {
        val f = forecast(listOf(habit(1, archived = true), habit(2, createdDaysAgo = -1)), emptyList())
        assertTrue(f.items.isEmpty())
        assertEquals(0, f.dueToday)
    }

    @Test fun otherHabitsRecentMissesRaiseTodaysRisk() {
        val target = run(1, 1..20, CompletionStatus.DONE)
        val habits = listOf(habit(1), habit(2))
        val goodDays = forecast(habits, target + run(2, 1..3, CompletionStatus.DONE))
        val roughDays = forecast(habits, target + run(2, 1..3, CompletionStatus.MISSED))
        val good = goodDays.items.single { it.habitId == 1L }.probability
        val rough = roughDays.items.single { it.habitId == 1L }.probability
        assertTrue("rough $rough should exceed good $good", rough > good)
    }
}
