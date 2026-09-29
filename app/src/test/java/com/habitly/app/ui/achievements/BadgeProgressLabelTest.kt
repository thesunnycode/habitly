package com.habitly.app.ui.achievements

import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeProgressLabelTest {
    @Test
    fun countsReadAsCountsAndTheMonthBadgeAsPercent() {
        val streak = AchievementProgress(AchievementType.STREAK_30, null, 12)
        val month = AchievementProgress(AchievementType.MONTH_80, null, 62)
        assertEquals("12/30", streak.progressLabel(compact = true))
        assertEquals("12 of 30", streak.progressLabel())
        assertEquals("62% / 80%", month.progressLabel(compact = true))
        assertEquals("62% of 80%", month.progressLabel())
    }
}
