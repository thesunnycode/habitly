package com.habitly.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.BarChart
import com.habitly.app.ui.components.LineChart
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard
import com.habitly.app.ui.components.StatTile
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(uiState: StatsUiState, today: LocalDate = LocalDate.now()) {
    val dayLabels = (6 downTo 0).map { offset ->
        today.minusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
    val bestDayIndex = uiState.weeklyCompletionByDay.indices.maxByOrNull { uiState.weeklyCompletionByDay[it] }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Stats",
                subtitle = "Your last 7 days at a glance",
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Completion",
                    value = "${(uiState.overallCompletionRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Best day",
                    value = bestDayIndex?.let { dayLabels[it] } ?: "—",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                StatTile(
                    label = "Days tracked",
                    value = "${uiState.weeklyCompletionByDay.count { it > 0f }}",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        item {
            SectionCard(title = "Completion trend", subtitle = "Share of habits done each day") {
                LineChart(values = uiState.weeklyCompletionByDay, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SectionCard(title = "Completion by day", subtitle = "Oldest day on the left") {
                BarChart(
                    values = uiState.weeklyCompletionByDay,
                    labels = dayLabels,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard(
                title = "Which days you keep it up",
                subtitle = uiState.weakestWeekday?.let { weakest ->
                    "Weakest day: ${weakest.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                } ?: "No history yet",
            ) {
                val weekdays = java.time.DayOfWeek.entries
                val counts = weekdays.map { (uiState.completionsByWeekday[it] ?: 0).toFloat() }
                val peak = counts.maxOrNull()?.takeIf { it > 0f } ?: 1f
                BarChart(
                    values = counts.map { it / peak },
                    labels = weekdays.map {
                        it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${uiState.totalCompletions} completions in total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard(title = "Highlights") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HighlightRow("Strongest habit", uiState.bestHabitName ?: "Not enough data yet")
                    HighlightRow("Needs attention", uiState.worstHabitName ?: "Nothing slipping — nice")
                }
            }
        }
    }
}

@Composable
private fun HighlightRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
