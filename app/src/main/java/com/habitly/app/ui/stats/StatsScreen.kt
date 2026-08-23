package com.habitly.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.BarChart
import com.habitly.app.ui.components.LineChart

@Composable
fun StatsScreen(uiState: StatsUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text("Stats") }) }) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Text("This week's completion trend", style = MaterialTheme.typography.titleMedium)
            LineChart(values = uiState.weeklyCompletionByDay, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

            Text("Completion by day", style = MaterialTheme.typography.titleMedium)
            BarChart(
                values = uiState.weeklyCompletionByDay,
                labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Text(
                text = "Overall completion: ${(uiState.overallCompletionRate * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
            )
            uiState.bestHabitName?.let { Text("Best habit: $it", style = MaterialTheme.typography.bodyLarge) }
            uiState.worstHabitName?.let { Text("Needs attention: $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
