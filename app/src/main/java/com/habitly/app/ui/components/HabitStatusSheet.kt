package com.habitly.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.CompletionStatus

/**
 * Long-press target for a single day. The point of the sheet is the middle option: a rest day the
 * user declares, which leaves the streak and the completion rate alone. Without it the only honest
 * way to record a day off is to take the hit, which is the moment people give up on a habit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitStatusSheet(
    habitName: String,
    current: CompletionStatus?,
    onSelect: (CompletionStatus?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = habitName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            StatusRow(
                icon = Icons.Filled.Check,
                label = "Done today",
                detail = "Counts towards the streak",
                selected = current == CompletionStatus.DONE,
                onClick = { onSelect(CompletionStatus.DONE) },
            )
            StatusRow(
                icon = Icons.Filled.Remove,
                label = "Rest day",
                detail = "Streak and completion rate stay as they are",
                selected = current == CompletionStatus.SKIPPED,
                onClick = { onSelect(CompletionStatus.SKIPPED) },
            )
            StatusRow(
                icon = Icons.Filled.Close,
                label = "Missed it",
                detail = "Ends the streak",
                selected = current == CompletionStatus.MISSED,
                onClick = { onSelect(CompletionStatus.MISSED) },
            )
            StatusRow(
                icon = Icons.Filled.RadioButtonUnchecked,
                label = "Clear",
                detail = "Leave the day unrecorded",
                selected = current == null,
                onClick = { onSelect(null) },
            )
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
