package com.habitly.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitCard
import com.habitly.app.ui.components.ProgressRing

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleHabit: (Long) -> Unit,
    onAddHabit: () -> Unit,
    onHabitClick: (Long) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Filled.Add, contentDescription = "Add habit")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProgressRing(progress = uiState.completionPercent)
                Text(text = "Today's progress", style = MaterialTheme.typography.bodyMedium)
            }
            if (uiState.habits.isEmpty() && !uiState.isLoading) {
                Text(
                    text = "No habits yet — tap + to add your first one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.habits, key = { it.habit.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            onToggle = { onToggleHabit(habit.habit.id) },
                            onClick = { onHabitClick(habit.habit.id) },
                        )
                    }
                }
            }
        }
    }
}
