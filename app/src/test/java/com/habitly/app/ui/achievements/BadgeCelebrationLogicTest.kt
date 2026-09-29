package com.habitly.app.ui.achievements

import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeCelebrationLogicTest {
    private val progress = listOf(
        AchievementProgress(AchievementType.FIRST_HABIT, unlockedAt = 1L, progress = 1),
        // Earned but not yet stored: the next evaluation would unlock it.
        AchievementProgress(AchievementType.STREAK_3, unlockedAt = null, progress = 3),
        AchievementProgress(AchievementType.STREAK_7, unlockedAt = null, progress = 3),
    )

    @Test
    fun firstRunWithEarnedBadgesMarksThemSeenSilently() {
        assertEquals(setOf("FIRST_HABIT", "STREAK_3"), silentFirstRunKeys(progress, seen = emptySet()))
    }

    @Test
    fun firstRunWithNothingEarnedIsNotSilent() {
        val nothing = listOf(AchievementProgress(AchievementType.STREAK_7, null, 0))
        assertNull(silentFirstRunKeys(nothing, seen = emptySet()))
    }

    @Test
    fun onceSomethingIsSeenFirstRunIsNotSilent() {
        assertNull(silentFirstRunKeys(progress, seen = setOf("STREAK_100")))
    }

    @Test
    fun newlyUnlockedSkipsSeenQueuedAndLockedBadges() {
        val list = progress + AchievementProgress(AchievementType.CHECKINS_10, unlockedAt = 2L, progress = 10) +
            AchievementProgress(AchievementType.EARLY_BIRD, unlockedAt = 3L, progress = 10)
        assertEquals(
            listOf(AchievementType.CHECKINS_10),
            newlyUnlocked(list, seen = setOf("FIRST_HABIT"), queued = setOf(AchievementType.EARLY_BIRD)),
        )
    }
}
