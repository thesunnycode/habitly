package com.habitly.app.ui.achievements

import com.habitly.app.domain.AchievementType

data class AchievementUiItem(val type: AchievementType, val unlocked: Boolean)
data class AchievementsUiState(val items: List<AchievementUiItem> = emptyList())
