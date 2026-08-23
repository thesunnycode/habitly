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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.habitly.app.data.repository.HabitWithStats

private fun parseHabitColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF6B9080))

/** White on a dark card, near-black on a light/pastel card — whichever the card color needs. */
private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1B1F1D) else Color.White

/**
 * Full-bleed solid-color card per habit ("Daily Habit Planner" style): the habit's own color
 * fills the whole card, with an icon badge, title, a short status line, and a round check
 * button that inverts (fills solid, icon flips to the card color) once completed.
 */
@Composable
fun HabitCard(
    habit: HabitWithStats,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = parseHabitColor(habit.habit.colorHex)
    val contentColor = contentColorFor(cardColor)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = habitIconFor(habit.habit.icon), contentDescription = null, tint = contentColor)
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    text = habit.habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    textDecoration = if (habit.isCompletedToday) TextDecoration.LineThrough else null,
                )
                Text(
                    text = statusLineFor(habit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (habit.isCompletedToday) {
                            Modifier.background(contentColor)
                        } else {
                            Modifier.border(2.dp, contentColor.copy(alpha = 0.6f), CircleShape)
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
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = cardColor)
                }
            }
        }
    }
}

private fun statusLineFor(habit: HabitWithStats): String {
    val parts = mutableListOf(habit.habit.category)
    habit.habit.reminderTime?.let { parts.add(it) }
    if (habit.currentStreak > 0) parts.add("🔥${habit.currentStreak}")
    return parts.joinToString("  •  ")
}
