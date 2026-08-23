package com.habitly.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitCard
import com.habitly.app.ui.components.ProgressRing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                HomeHeader()
                Row(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressRing(progress = uiState.completionPercent)
                        Text(text = "Today's progress", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(modifier = Modifier.weight(2f)) {
                        TextButton(onClick = onSeeAllClick, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("See all")
                        }
                        HabitListContent(
                            uiState = uiState,
                            onToggleHabit = onToggleHabit,
                            onHabitClick = onHabitClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                HomeHeader()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${uiState.habits.count { it.isCompletedToday }} / ${uiState.habits.size} done",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = "Keep the streak alive", style = MaterialTheme.typography.bodyMedium)
                    }
                    ProgressRing(progress = uiState.completionPercent)
                }
                TextButton(onClick = onSeeAllClick, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("See all")
                }
                HabitListContent(
                    uiState = uiState,
                    onToggleHabit = onToggleHabit,
                    onHabitClick = onHabitClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    val today = remember { LocalDate.now() }
    Column {
        Text(
            text = "TODAY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
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
            modifier = modifier,
        )
    } else {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
