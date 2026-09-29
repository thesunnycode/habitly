package com.habitly.app.ui.achievements

import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementGroup

data class AchievementsUiState(
    val items: List<AchievementProgress> = emptyList(),
    val unlockedCount: Int = 0,
    val total: Int = 16,
    val groups: Map<AchievementGroup, List<AchievementProgress>> = emptyMap(),
)
