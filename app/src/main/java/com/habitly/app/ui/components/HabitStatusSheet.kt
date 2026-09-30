package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors

/**
 * Long-press target for a single day. The point of the sheet is the middle option: a rest day the
 * user declares, which leaves the streak and the completion rate alone. Without it the only honest
 * way to record a day off is to take the hit, which is the moment people give up on a habit.
 * [onSelect] receives the chosen status (null = clear the day) and the trimmed note, if any.
 */
@Composable
fun HabitStatusSheet(
    habitName: String,
    current: CompletionStatus?,
    onSelect: (CompletionStatus?, String?) -> Unit,
    onDismiss: () -> Unit,
    initialNote: String? = null,
) {
    HabitlySheet(onDismiss = onDismiss, title = habitName) {
        HabitStatusSheetContent(current, initialNote, onSelect)
    }
}

@Composable
private fun ColumnScope.HabitStatusSheetContent(
    current: CompletionStatus?,
    initialNote: String?,
    onSelect: (CompletionStatus?, String?) -> Unit,
) {
    var note by rememberSaveable { mutableStateOf(initialNote.orEmpty()) }
    val trimmedNote = note.trim().takeIf { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow(
            icon = Icons.Rounded.Check,
            label = "Done",
            detail = "Counts towards the streak",
            selected = current == CompletionStatus.DONE,
            onClick = { onSelect(CompletionStatus.DONE, trimmedNote) },
        )
        StatusRow(
            icon = Icons.Rounded.Weekend,
            label = "Rest day",
            detail = "Streak and completion rate stay as they are",
            selected = current == CompletionStatus.SKIPPED,
            onClick = { onSelect(CompletionStatus.SKIPPED, trimmedNote) },
        )
        StatusRow(
            icon = Icons.Rounded.Close,
            label = "Clear",
            detail = "Leave the day unrecorded",
            selected = current == null,
            onClick = { onSelect(null, null) },
        )
    }
    Spacer(Modifier.height(16.dp))
    TextField(
        value = note,
        onValueChange = { note = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Add a note (saved with Done or Rest day)") },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        maxLines = 3,
    )
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = LocalHabitlyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) scheme.primaryContainer else extras.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Current", tint = scheme.primary)
        }
    }
}

@Composable
private fun HabitStatusSheetPreviewContent() {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = "Morning run") {
            HabitStatusSheetContent(CompletionStatus.SKIPPED, null) { _, _ -> }
        }
    }
}

@Preview @Composable private fun HabitStatusSheetDarkPreview() = HabitlyTheme(darkTheme = true) { HabitStatusSheetPreviewContent() }
@Preview @Composable private fun HabitStatusSheetLightPreview() = HabitlyTheme(darkTheme = false) { HabitStatusSheetPreviewContent() }
