package com.habitly.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.habitly.app.ui.components.ProgressRing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleHabit: (Long) -> Unit,
    onAddHabit: () -> Unit,
    onHabitClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit = {},
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    now: LocalTime = LocalTime.now(),
    today: LocalDate = LocalDate.now(),
) {
    val doneCount = uiState.habits.count { it.isCompletedToday }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Filled.Add, contentDescription = "Add habit")
            }
        },
    ) { padding ->
        // Bottom inset only — MainActivity already applied the status-bar inset once.
        val outerPadding = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())

        if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
            Row(modifier = outerPadding.padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.weight(1f).padding(end = 20.dp)) {
                    Greeting(now, today)
                    TodaySummaryCard(doneCount, uiState.habits.size, uiState.completionPercent)
                }
                LazyColumn(
                    modifier = Modifier.weight(2f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { SectionHeader(uiState.habits.size, onSeeAllClick) }
                    habitList(uiState, onToggleHabit, onHabitClick)
                }
            }
        } else {
            LazyColumn(
                modifier = outerPadding,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Greeting(now, today) }
                item { TodaySummaryCard(doneCount, uiState.habits.size, uiState.completionPercent) }
                item { SectionHeader(uiState.habits.size, onSeeAllClick) }
                habitList(uiState, onToggleHabit, onHabitClick)
            }
        }
    }
}

private fun LazyListScope.habitList(
    uiState: HomeUiState,
    onToggleHabit: (Long) -> Unit,
    onHabitClick: (Long) -> Unit,
) {
    if (uiState.habits.isEmpty() && !uiState.isLoading) {
        item {
            Text(
                text = "No habits yet — tap + to add your first one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    } else {
        items(uiState.habits, key = { it.habit.id }) { habit ->
            HabitCard(
                habit = habit,
                onToggle = { onToggleHabit(habit.habit.id) },
                onClick = { onHabitClick(habit.habit.id) },
            )
        }
    }
}

@Composable
private fun Greeting(now: LocalTime, today: LocalDate) {
    val greeting = when {
        now.hour < 12 -> "Good morning"
        now.hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The day's score in one glance: ring, count, and what's left to do. */
@Composable
private fun TodaySummaryCard(done: Int, total: Int, percent: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today's progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "$done of $total done",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = when {
                        total == 0 -> "Add a habit to get started."
                        done == total -> "Every habit done — enjoy the streak."
                        else -> "${total - done} left to keep your streaks alive."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            ProgressRing(progress = percent)
        }
    }
}

@Composable
private fun SectionHeader(count: Int, onSeeAllClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                Text(text = "$count", style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onSeeAllClick) { Text("See all") }
    }
}
