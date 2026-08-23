package com.habitly.app.ui.addedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.FrequencyType
import java.time.DayOfWeek

@Composable
fun AddEditHabitScreen(
    uiState: AddEditUiState,
    onNameChange: (String) -> Unit,
    onFrequencyTypeChange: (FrequencyType) -> Unit,
    onSpecificDaysChange: (Set<DayOfWeek>) -> Unit,
    onIntervalDaysChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.habitId == null) "Add Habit" else "Edit Habit") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Habit name") },
                isError = uiState.nameError != null,
                supportingText = { uiState.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Frequency",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            LazyRow {
                items(FrequencyType.entries) { type ->
                    FilterChip(
                        selected = uiState.frequencyType == type,
                        onClick = { onFrequencyTypeChange(type) },
                        label = { Text(type.name.lowercase().replace('_', ' ')) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            if (uiState.frequencyType == FrequencyType.SPECIFIC_DAYS) {
                Text(
                    text = "Days",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                LazyRow {
                    items(DayOfWeek.entries) { day ->
                        FilterChip(
                            selected = day in uiState.specificDays,
                            onClick = {
                                val updated = uiState.specificDays.toMutableSet()
                                if (day in updated) updated.remove(day) else updated.add(day)
                                onSpecificDaysChange(updated)
                            },
                            label = { Text(day.name.take(3)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }

            if (uiState.frequencyType == FrequencyType.INTERVAL) {
                OutlinedTextField(
                    value = uiState.intervalDays?.toString() ?: "",
                    onValueChange = { onIntervalDaysChange(it.toIntOrNull()) },
                    label = { Text("Every N days") },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }

            Button(onClick = onSave, modifier = Modifier.padding(top = 24.dp)) {
                Text("Save Habit")
            }
        }
    }
}
