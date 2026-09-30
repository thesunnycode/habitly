package com.habitly.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.AchievementGroup
import com.habitly.app.domain.AchievementType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class AchievementsViewModel(
    private val repository: HabitDataSource,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAchievementProgress(today).collect { items ->
                val byGroup = items.groupBy { it.type.group }
                _uiState.value = AchievementsUiState(
                    items = items,
                    unlockedCount = items.count { it.unlockedAt != null },
                    total = AchievementType.entries.size,
                    // Every group keeps its place in the fixed order, even while it has nothing to show.
                    groups = AchievementGroup.entries.associateWith { byGroup[it].orEmpty() },
                )
            }
        }
    }
}
