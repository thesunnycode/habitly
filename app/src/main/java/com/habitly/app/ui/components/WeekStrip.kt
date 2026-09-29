package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven tappable days. Without this there is no way to record a habit you forgot yesterday.
 * [describe] supplies the spoken description (e.g. "Tuesday 29 September, 3 of 4 done").
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    completion: (LocalDate) -> Float,
    describe: (LocalDate) -> String,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            DayChip(
                day = day,
                isSelected = day == selected,
                isToday = day == today,
                completion = completion(day),
                description = describe(day),
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    completion: Float,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalHabitlyColors.current
    val scheme = MaterialTheme.colorScheme
    val label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val dot = when {
        completion >= 1f -> extras.success
        completion > 0f -> lerp(scheme.primaryContainer, scheme.primary, 0.5f)
        else -> extras.gridEmpty
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (isSelected) extras.primaryButton else scheme.surface)
            // Today keeps a ring when another day is selected, so the way back is always visible.
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.take(3),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) extras.onPrimaryButton.copy(alpha = 0.7f) else extras.onSurfaceFaint,
        )
        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) extras.onPrimaryButton else scheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
    }
}

@Composable
private fun WeekStripPreviewContent() {
    val today = LocalDate.of(2026, 9, 29)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    Surface(color = MaterialTheme.colorScheme.background) {
        WeekStrip(
            days = days,
            selected = today.minusDays(2),
            completion = { if (it.dayOfMonth % 3 == 0) 1f else if (it.dayOfMonth % 2 == 0) 0.5f else 0f },
            describe = { it.toString() },
            onSelect = {},
            today = today,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview @Composable private fun WeekStripDarkPreview() = HabitlyTheme(darkTheme = true) { WeekStripPreviewContent() }
@Preview @Composable private fun WeekStripLightPreview() = HabitlyTheme(darkTheme = false) { WeekStripPreviewContent() }
