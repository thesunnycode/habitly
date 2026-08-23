package com.habitly.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.CalendarHeatmap
import com.habitly.app.ui.components.ConfirmDialog

@Composable
fun HabitDetailScreen(
    uiState: HabitDetailUiState,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMonthChange: (java.time.YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.habit?.name ?: "") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = onArchive) { Text("Archive") }
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.padding(end = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Current streak", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiState.currentStreak}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Card(modifier = Modifier.padding(end = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Best streak", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiState.bestStreak}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Completion", style = MaterialTheme.typography.bodyMedium)
                        Text("${(uiState.completionRate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Text(
                text = uiState.month.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            CalendarHeatmap(records = uiState.records, month = uiState.month)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete habit?",
            message = "This permanently deletes the habit and all its history.",
            confirmLabel = "Delete",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
