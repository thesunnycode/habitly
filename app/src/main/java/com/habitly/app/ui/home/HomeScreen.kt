package com.habitly.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitCard

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleHabit: (Long) -> Unit,
    onAddHabit: () -> Unit,
    onHabitClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit = {},
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Filled.Add, contentDescription = "Add habit")
            }
        },
    ) { padding ->
        if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.weight(1f).padding(24.dp)) {
                    ScreenTitle()
                }
                Column(modifier = Modifier.weight(2f).padding(24.dp)) {
                    TodaySectionHeader(count = uiState.habits.size, onSeeAllClick = onSeeAllClick)
                    HabitListContent(uiState, onToggleHabit, onHabitClick, Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                ScreenTitle()
                TodaySectionHeader(count = uiState.habits.size, onSeeAllClick = onSeeAllClick)
                HabitListContent(uiState, onToggleHabit, onHabitClick, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = "Habits",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun TodaySectionHeader(count: Int, onSeeAllClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$count", style = MaterialTheme.typography.labelLarge)
            }
        }
        TextButton(onClick = onSeeAllClick) { Text("See all") }
    }
}

@Composable
private fun HabitListContent(
    uiState: HomeUiState,
    onToggleHabit: (Long) -> Unit,
    onHabitClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.habits.isEmpty() && !uiState.isLoading) {
        Text(
            text = "No habits yet — tap + to add your first one.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier.padding(top = 24.dp),
        )
    } else {
        LazyColumn(modifier = modifier) {
            items(uiState.habits, key = { it.habit.id }) { habit ->
                HabitCard(
                    habit = habit,
                    onToggle = { onToggleHabit(habit.habit.id) },
                    onClick = { onHabitClick(habit.habit.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
