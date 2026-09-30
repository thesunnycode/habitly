package com.habitly.app.ui.calendar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.ui.components.CheckButton
import com.habitly.app.ui.components.CheckState
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.IconTile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHEET_TITLE = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())

/** One day's due habits, each editable with its check button. */
@Composable
fun DaySheet(
    date: LocalDate,
    dayHabits: List<DayHabit>,
    onToggle: (habitId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val done = dayHabits.count { it.status == CompletionStatus.DONE }
    // Rested habits stay listed but, being neutral, are not part of "n of m".
    val counted = dayHabits.count { it.status != CompletionStatus.SKIPPED }
    HabitlySheet(onDismiss = onDismiss, title = date.format(SHEET_TITLE)) {
        Text(
            text = when {
                dayHabits.isEmpty() -> "Nothing was due this day"
                counted == 0 -> "Rest day"
                else -> "$done of $counted done"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        dayHabits.forEach { day -> DayHabitRow(day, onToggle = { onToggle(day.habit.id) }) }
    }
}

@Composable
private fun DayHabitRow(day: DayHabit, onToggle: () -> Unit) {
    val target = day.habit.targetCount
    val state = when {
        day.status == CompletionStatus.DONE -> CheckState.DONE
        day.status == CompletionStatus.SKIPPED -> CheckState.SKIPPED
        day.status == CompletionStatus.PARTIAL || (target != null && day.progress > 0) -> CheckState.IN_PROGRESS
        else -> CheckState.EMPTY
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(iconKey = day.habit.icon, colorHex = day.habit.colorHex, size = 32.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = day.habit.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            if (target != null) {
                Text(
                    text = "${day.progress} of $target${day.habit.unit?.let { " $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CheckButton(
            state = state,
            colorHex = day.habit.colorHex,
            onClick = onToggle,
            contentDescription = day.habit.name,
        )
    }
}
