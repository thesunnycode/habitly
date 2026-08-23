package com.habitly.app.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.FrequencyType
import com.habitly.app.ui.components.HABIT_COLOR_SWATCHES
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import com.habitly.app.ui.components.habitIconFor
import java.time.DayOfWeek

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF6B9080))

@Composable
fun AddEditHabitScreen(
    uiState: AddEditUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onFrequencyTypeChange: (FrequencyType) -> Unit,
    onSpecificDaysChange: (Set<DayOfWeek>) -> Unit,
    onIntervalDaysChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val selectedColor = parseColor(uiState.colorHex)

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
                text = "Icon",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HABIT_ICON_KEYS) { key ->
                    val selected = uiState.icon == key
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) selectedColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) selectedColor else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { onIconChange(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = habitIconFor(key),
                            contentDescription = key,
                            tint = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "Color",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HABIT_COLOR_SWATCHES) { hex ->
                    val swatchColor = parseColor(hex)
                    val selected = uiState.colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .clickable { onColorChange(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
                        }
                    }
                }
            }

            Text(
                text = "Frequency",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
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
