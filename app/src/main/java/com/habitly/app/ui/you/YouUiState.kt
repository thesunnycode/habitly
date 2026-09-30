package com.habitly.app.ui.you

import com.habitly.app.data.repository.AchievementProgress
import java.time.YearMonth

data class YouUiState(
    val name: String? = null,
    /** The month of the earliest habit, or null before there is one. */
    val memberSince: YearMonth? = null,
    val activeHabits: Int = 0,
    val bestStreak: Int = 0,
    val checkIns: Int = 0,
    val perfectDays: Int = 0,
    /** Up to three most recently unlocked badges, newest first. */
    val recentBadges: List<AchievementProgress> = emptyList(),
    /** The locked badge nearest to unlocking, by progress over target. */
    val nextBadge: AchievementProgress? = null,
    val unlockedCount: Int = 0,
)
