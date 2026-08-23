package com.habitly.app.domain

enum class AchievementType(val streakThreshold: Int?) {
    FIRST_HABIT(null),
    STREAK_7(7),
    STREAK_30(30),
    STREAK_100(100),
}

object AchievementEvaluator {

    fun evaluate(
        bestStreak: Int,
        isFirstHabitEver: Boolean,
        alreadyUnlocked: Set<AchievementType>,
    ): Set<AchievementType> {
        val newly = mutableSetOf<AchievementType>()

        if (isFirstHabitEver && AchievementType.FIRST_HABIT !in alreadyUnlocked) {
            newly += AchievementType.FIRST_HABIT
        }

        for (type in AchievementType.entries) {
            val threshold = type.streakThreshold ?: continue
            if (bestStreak >= threshold && type !in alreadyUnlocked) {
                newly += type
            }
        }

        return newly
    }
}
