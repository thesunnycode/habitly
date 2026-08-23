package com.habitly.app.ui.achievements

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun labelFor(type: com.habitly.app.domain.AchievementType) = when (type) {
    com.habitly.app.domain.AchievementType.FIRST_HABIT -> "First Habit"
    com.habitly.app.domain.AchievementType.STREAK_7 -> "7-Day Streak"
    com.habitly.app.domain.AchievementType.STREAK_30 -> "30-Day Streak"
    com.habitly.app.domain.AchievementType.STREAK_100 -> "100-Day Streak"
}

@Composable
fun AchievementsScreen(uiState: AchievementsUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text("Achievements") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
        ) {
            items(uiState.items) { item ->
                Card(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = labelFor(item.type),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
