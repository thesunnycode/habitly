package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven tappable days ending today. Without this there is no way to record a habit you forgot
 * yesterday, which is the single capability every reference app has and this one did not.
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            DayChip(
                day = day,
                isSelected = day == selected,
                isToday = day == today,
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val description = if (isToday) "Today, ${day.dayOfMonth} ${day.month}" else "$label ${day.dayOfMonth} ${day.month}"

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = isSelected, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clearAndSetSemantics {
                this.contentDescription = description
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.take(3),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                )
                .then(
                    // Today keeps a ring even when another day is selected, so you never lose your place.
                    if (isToday && !isSelected) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${day.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
