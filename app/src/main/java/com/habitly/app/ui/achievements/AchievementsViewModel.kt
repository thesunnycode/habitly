package com.habitly.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.AchievementType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AchievementsViewModel(private val repository: HabitDataSource) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAchievements().collect { unlocked ->
                val unlockedTypes = unlocked.mapNotNull { runCatching { AchievementType.valueOf(it.type) }.getOrNull() }.toSet()
                _uiState.value = AchievementsUiState(
                    items = AchievementType.entries.map { type -> AchievementUiItem(type, type in unlockedTypes) },
                )
            }
        }
    }
}
