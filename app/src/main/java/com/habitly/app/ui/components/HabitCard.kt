package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.habitly.app.data.repository.HabitWithStats

/**
 * Flat, minimal task-list-style row (TickTick-inspired): a plain square checkbox,
 * the habit name, and a trailing reminder time or streak count. No card background,
 * no per-habit color — one accent color across the whole list, matching the cloned
 * reference's plain functional look.
 */
@Composable
fun HabitCard(
    habit: HabitWithStats,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (habit.isCompletedToday) {
                        Modifier.background(accent)
                    } else {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    }
                )
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = if (habit.isCompletedToday) {
                        "Mark ${habit.habit.name} as not done today"
                    } else {
                        "Mark ${habit.habit.name} as done today"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (habit.isCompletedToday) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = habit.habit.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (habit.isCompletedToday) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (habit.isCompletedToday) TextDecoration.LineThrough else null,
            )
            Text(
                text = habit.habit.category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val trailingLabel = habit.habit.reminderTime ?: if (habit.currentStreak > 0) "🔥${habit.currentStreak}" else null
        trailingLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
    }
}
