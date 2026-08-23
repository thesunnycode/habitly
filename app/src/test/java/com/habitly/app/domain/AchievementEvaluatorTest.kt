package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementEvaluatorTest {

    @Test
    fun unlocksStreak7WhenBestStreakReaches7() {
        val newly = AchievementEvaluator.evaluate(bestStreak = 7, isFirstHabitEver = false, alreadyUnlocked = emptySet())
        assertEquals(setOf(AchievementType.STREAK_7), newly)
    }

    @Test
    fun unlocksStreak7And30TogetherWhenBestStreakJumpsPastBoth() {
        val newly = AchievementEvaluator.evaluate(bestStreak = 30, isFirstHabitEver = false, alreadyUnlocked = emptySet())
        assertEquals(setOf(AchievementType.STREAK_7, AchievementType.STREAK_30), newly)
    }

    @Test
    fun doesNotReUnlockAnAchievementAlreadyHeld() {
        val newly = AchievementEvaluator.evaluate(
            bestStreak = 7, isFirstHabitEver = false, alreadyUnlocked = setOf(AchievementType.STREAK_7)
        )
        assertEquals(emptySet<AchievementType>(), newly)
    }

    @Test
    fun unlocksFirstHabitAchievementIndependentlyOfStreak() {
        val newly = AchievementEvaluator.evaluate(bestStreak = 0, isFirstHabitEver = true, alreadyUnlocked = emptySet())
        assertEquals(setOf(AchievementType.FIRST_HABIT), newly)
    }

    @Test
    fun noUnlocksWhenBelowAllThresholds() {
        val newly = AchievementEvaluator.evaluate(bestStreak = 3, isFirstHabitEver = false, alreadyUnlocked = emptySet())
        assertEquals(emptySet<AchievementType>(), newly)
    }
}
