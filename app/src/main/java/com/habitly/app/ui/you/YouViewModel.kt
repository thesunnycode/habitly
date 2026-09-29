package com.habitly.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.prefs.YouPrefs
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.AchievementSnapshotBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

class YouViewModel(
    private val repository: HabitDataSource,
    private val prefs: YouPrefs,
    private val today: () -> LocalDate = { LocalDate.now() },
    /** Where the lifetime snapshot is built: off the main thread in production, the test dispatcher in tests. */
    private val computeDispatcher: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouUiState())
    val uiState: StateFlow<YouUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeAllHabits(),
                repository.observeAllLogs(),
                repository.observeAchievementProgress(today()),
                prefs.userName,
            ) { habits, logs, progress, name -> build(habits, logs, progress, name) }
                .flowOn(computeDispatcher)
                .collect { _uiState.value = it }
        }
    }

    fun onNameChange(name: String) {
        viewModelScope.launch { prefs.setUserName(name) }
    }

    private fun build(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        progress: List<AchievementProgress>,
        name: String?,
    ): YouUiState {
        val zone = ZoneId.systemDefault()
        val snapshot = AchievementSnapshotBuilder.build(habits, logs, today(), zone)
        val unlocked = progress.filter { it.unlockedAt != null }
        return YouUiState(
            name = name,
            memberSince = habits.minOfOrNull { it.createdAt }
                ?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) },
            activeHabits = snapshot.activeHabits,
            bestStreak = snapshot.bestStreak,
            checkIns = snapshot.totalCheckIns,
            perfectDays = snapshot.perfectDays,
            recentBadges = unlocked.sortedByDescending { it.unlockedAt }.take(3),
            nextBadge = progress.filter { it.unlockedAt == null }
                .maxByOrNull { it.progress.toFloat() / it.type.target },
            unlockedCount = unlocked.size,
        )
    }
}
