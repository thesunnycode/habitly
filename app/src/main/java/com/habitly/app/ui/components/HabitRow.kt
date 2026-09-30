package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones

/** Compact list row: icon, name and supporting text, a seven-day mini strip, optional trailing slot. */
@Composable
fun HabitRow(
    name: String,
    iconKey: String,
    colorHex: String,
    supporting: String,
    last7: List<CheckState>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    muted: Boolean = false,
) {
    val tones = habitTones(colorHex)
    val empty = LocalHabitlyColors.current.gridEmpty
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (muted) 0.55f else 1f)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(iconKey, colorHex, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            last7.takeLast(7).forEach { state ->
                val color = when (state) {
                    CheckState.DONE -> tones.full
                    CheckState.SKIPPED -> tones.low
                    CheckState.IN_PROGRESS -> tones.mid
                    CheckState.EMPTY -> empty
                }
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun HabitRowPreviewContent() {
    val week = listOf(
        CheckState.DONE, CheckState.DONE, CheckState.SKIPPED, CheckState.IN_PROGRESS,
        CheckState.EMPTY, CheckState.DONE, CheckState.DONE,
    )
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp)) {
            HabitRow("Morning run", "run", "#FF922B", "5 of 7 days", week, onClick = {})
            HabitRow("Read", "menu_book", "#9775FA", "Archived", week, onClick = {}, muted = true)
        }
    }
}

@Preview @Composable private fun HabitRowDarkPreview() = HabitlyTheme(darkTheme = true) { HabitRowPreviewContent() }
@Preview @Composable private fun HabitRowLightPreview() = HabitlyTheme(darkTheme = false) { HabitRowPreviewContent() }
