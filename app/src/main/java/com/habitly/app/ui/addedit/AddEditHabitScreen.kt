package com.habitly.app.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.ui.components.HABIT_COLOR_SWATCHES
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard
import com.habitly.app.ui.components.habitIconFor
import java.time.DayOfWeek

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF6B9080))

private fun labelFor(type: FrequencyType) = when (type) {
    FrequencyType.DAILY -> "Daily"
    FrequencyType.SPECIFIC_DAYS -> "Certain days"
    FrequencyType.INTERVAL -> "Every N days"
}

@Composable
fun AddEditHabitScreen(
    uiState: AddEditUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onFrequencyTypeChange: (FrequencyType) -> Unit,
    onSpecificDaysChange: (Set<DayOfWeek>) -> Unit,
    onIntervalDaysChange: (Int?) -> Unit,
    onTargetCountChange: (Int?) -> Unit = {},
    onUnitChange: (String) -> Unit = {},
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTemplateSelected: (HabitTemplate) -> Unit = {},
) {
    val selectedColor = parseColor(uiState.colorHex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = if (uiState.habitId == null) "New habit" else "Edit habit",
                subtitle = "Name it, pick a look, choose how often",
                onBack = onCancel,
            )
        }

        // Showing the card they're building removes the guesswork from icon and colour choice.
        item { HabitPreview(name = uiState.name, icon = uiState.icon, color = selectedColor) }

        // Only offered for a brand new habit: an empty first session is where habit apps lose
        // people, and one tap here fills the name, icon, colour, category and schedule together.
        if (uiState.habitId == null) {
            item {
                SectionCard(title = "Start from a template", subtitle = "Or scroll down and write your own") {
                    HabitTemplates.groups.forEach { group ->
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(group.templates) { template ->
                                TemplateChip(
                                    template = template,
                                    selected = uiState.name == template.name,
                                    onClick = { onTemplateSelected(template) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Name") {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    placeholder = { Text("e.g. Drink water") },
                    isError = uiState.nameError != null,
                    supportingText = { uiState.nameError?.let { Text(it) } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SectionCard(title = "Icon") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HABIT_ICON_KEYS) { key ->
                        val selected = uiState.icon == key
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        selectedColor.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
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
            }
        }

        item {
            SectionCard(title = "Colour") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HABIT_COLOR_SWATCHES) { hex ->
                        val swatchColor = parseColor(hex)
                        val selected = uiState.colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .clickable { onColorChange(hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Daily goal",
                subtitle = "Optional — leave blank for a simple done or not done",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = uiState.targetCount?.toString() ?: "",
                        onValueChange = { onTargetCountChange(it.toIntOrNull()) },
                        placeholder = { Text("8") },
                        label = { Text("Target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = uiState.unit,
                        onValueChange = onUnitChange,
                        placeholder = { Text("cups") },
                        label = { Text("Unit") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1.4f),
                    )
                }
                if ((uiState.targetCount ?: 0) > 1) {
                    Text(
                        text = "Tapping the card adds one ${uiState.unit.trim().ifBlank { "unit" }} " +
                            "until you reach ${uiState.targetCount}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            SectionCard(title = "Frequency", subtitle = "How often this habit should come round") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FrequencyType.entries) { type ->
                        FilterChip(
                            selected = uiState.frequencyType == type,
                            onClick = { onFrequencyTypeChange(type) },
                            label = { Text(labelFor(type)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                if (uiState.frequencyType == FrequencyType.SPECIFIC_DAYS) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        items(DayOfWeek.entries) { day ->
                            FilterChip(
                                selected = day in uiState.specificDays,
                                onClick = {
                                    val updated = uiState.specificDays.toMutableSet()
                                    if (day in updated) updated.remove(day) else updated.add(day)
                                    onSpecificDaysChange(updated)
                                },
                                label = { Text(day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }

                if (uiState.frequencyType == FrequencyType.INTERVAL) {
                    OutlinedTextField(
                        value = uiState.intervalDays?.toString() ?: "",
                        onValueChange = { onIntervalDaysChange(it.toIntOrNull()) },
                        label = { Text("Every N days") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }

        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = if (uiState.habitId == null) "Create habit" else "Save changes",
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TemplateChip(template: HabitTemplate, selected: Boolean, onClick: () -> Unit) {
    val accent = parseColor(template.colorHex)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = habitIconFor(template.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = template.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HabitPreview(name: String, icon: String, color: Color) {
    val contentColor = if (color.luminance() > 0.5f) Color(0xFF1B1F1D) else Color.White
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(habitIconFor(icon), contentDescription = null, tint = contentColor)
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = name.ifBlank { "Your new habit" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}
