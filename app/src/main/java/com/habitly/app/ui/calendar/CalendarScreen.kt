package com.habitly.app.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.CalendarHeatmap

@Composable
fun CalendarScreen(uiState: CalendarUiState, onMonthChange: (java.time.YearMonth) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Calendar") }) }) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onMonthChange(uiState.month.minusMonths(1)) }) { Text("<") }
                Text(
                    text = uiState.month.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                IconButton(onClick = { onMonthChange(uiState.month.plusMonths(1)) }) { Text(">") }
            }
            CalendarHeatmap(
                records = uiState.mergedRecords,
                month = uiState.month,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
