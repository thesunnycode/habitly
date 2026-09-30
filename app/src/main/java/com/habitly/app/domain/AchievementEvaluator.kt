package com.habitly.app.domain

enum class AchievementGroup(val title: String) { STREAKS("Streaks"), CONSISTENCY("Consistency"), VOLUME("Volume"), SPECIAL("Special") }

/** The badge set. Names are stored in the achievements table, so existing keys must never be renamed. */
enum class AchievementType(val group: AchievementGroup, val title: String, val criterion: String, val target: Int) {
    STREAK_3(AchievementGroup.STREAKS, "Warming up", "Keep any habit 3 days in a row", 3),
    STREAK_7(AchievementGroup.STREAKS, "One week", "Keep any habit 7 days in a row", 7),
    STREAK_30(AchievementGroup.STREAKS, "One month", "Keep any habit 30 days in a row", 30),
    STREAK_100(AchievementGroup.STREAKS, "Centurion", "Keep any habit 100 days in a row", 100),
    PERFECT_DAY(AchievementGroup.CONSISTENCY, "Perfect day", "Finish every habit due in one day", 1),
    PERFECT_WEEK(AchievementGroup.CONSISTENCY, "Perfect week", "Seven perfect days in a row", 7),
    MONTH_80(AchievementGroup.CONSISTENCY, "Strong month", "Keep 80% of a calendar month", 80),
    COMEBACK(AchievementGroup.CONSISTENCY, "Comeback", "Pick a habit back up after a broken streak", 1),
    CHECKINS_10(AchievementGroup.VOLUME, "Getting going", "10 check-ins in total", 10),
    CHECKINS_100(AchievementGroup.VOLUME, "Hundred club", "100 check-ins in total", 100),
    CHECKINS_500(AchievementGroup.VOLUME, "Habit machine", "500 check-ins in total", 500),
    GOAL_10(AchievementGroup.VOLUME, "Goal getter", "Meet a daily goal 10 times", 10),
    FIRST_HABIT(AchievementGroup.SPECIAL, "First step", "Create your first habit", 1),
    FIVE_HABITS(AchievementGroup.SPECIAL, "Full plate", "Track 5 habits at once", 5),
    EARLY_BIRD(AchievementGroup.SPECIAL, "Early bird", "10 check-ins before 8am", 10),
    SECOND_CHANCE(AchievementGroup.SPECIAL, "Second chance", "Mark a day as rest instead of breaking a streak", 1),
}

object AchievementEvaluator {
    /** How far along [type] is, from 0 up to its target. */
    fun progress(type: AchievementType, s: AchievementSnapshot): Int {
        val raw = when (type) {
            AchievementType.STREAK_3, AchievementType.STREAK_7,
            AchievementType.STREAK_30, AchievementType.STREAK_100 -> s.bestStreak
            AchievementType.PERFECT_DAY -> s.perfectDays
            AchievementType.PERFECT_WEEK -> s.longestPerfectRun
            AchievementType.MONTH_80 -> s.bestMonthPercent
            AchievementType.COMEBACK -> s.comebacks
            AchievementType.CHECKINS_10, AchievementType.CHECKINS_100, AchievementType.CHECKINS_500 -> s.totalCheckIns
            AchievementType.GOAL_10 -> s.goalDaysMet
            AchievementType.FIRST_HABIT -> s.habitsEver
            AchievementType.FIVE_HABITS -> s.activeHabits
            AchievementType.EARLY_BIRD -> s.earlyCheckIns
            AchievementType.SECOND_CHANCE -> s.restDays
        }
        return raw.coerceIn(0, type.target)
    }

    /** Badges [s] has earned that are not already in [alreadyUnlocked]. */
    fun evaluate(s: AchievementSnapshot, alreadyUnlocked: Set<AchievementType>): Set<AchievementType> =
        AchievementType.entries.filter { it !in alreadyUnlocked && progress(it, s) >= it.target }.toSet()
}
