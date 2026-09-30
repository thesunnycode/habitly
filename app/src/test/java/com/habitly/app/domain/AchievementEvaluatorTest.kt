package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorTest {
    @Test fun `sixteen badges in four groups of four`() {
        assertEquals(16, AchievementType.entries.size)
        AchievementGroup.entries.forEach { g -> assertEquals(4, AchievementType.entries.count { it.group == g }) }
    }
    @Test fun `existing stored keys are kept`() {
        listOf("FIRST_HABIT", "STREAK_7", "STREAK_30", "STREAK_100").forEach { AchievementType.valueOf(it) }
    }
    @Test fun `streak badges unlock at their thresholds`() {
        val got = AchievementEvaluator.evaluate(AchievementSnapshot(bestStreak = 7), emptySet())
        assertTrue(AchievementType.STREAK_3 in got && AchievementType.STREAK_7 in got)
        assertFalse(AchievementType.STREAK_30 in got)
    }
    @Test fun `already unlocked badges are not returned again`() {
        val got = AchievementEvaluator.evaluate(AchievementSnapshot(bestStreak = 7), setOf(AchievementType.STREAK_3))
        assertFalse(AchievementType.STREAK_3 in got)
    }
    @Test fun `progress is capped at the target`() {
        assertEquals(30, AchievementEvaluator.progress(AchievementType.STREAK_30, AchievementSnapshot(bestStreak = 45)))
        assertEquals(12, AchievementEvaluator.progress(AchievementType.STREAK_30, AchievementSnapshot(bestStreak = 12)))
    }
    @Test fun `every badge maps to its snapshot field`() {
        val s = AchievementSnapshot(perfectDays = 1, longestPerfectRun = 7, bestMonthPercent = 85, comebacks = 1,
            totalCheckIns = 500, goalDaysMet = 10, habitsEver = 1, activeHabits = 5, earlyCheckIns = 10, restDays = 1)
        val got = AchievementEvaluator.evaluate(s, emptySet())
        (AchievementType.entries - setOf(AchievementType.STREAK_3, AchievementType.STREAK_7,
            AchievementType.STREAK_30, AchievementType.STREAK_100)).forEach { assertTrue("$it", it in got) }
    }
}
