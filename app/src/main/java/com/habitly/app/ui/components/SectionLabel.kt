package com.habitly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors

/** Tracked, uppercase group heading, optionally with a count and a trailing action. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase() + (count?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = LocalHabitlyColors.current.onSurfaceFaint,
        )
        trailing?.invoke()
    }
}

@Composable
private fun SectionLabelPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Morning")
            SectionLabel("Today", count = 4)
        }
    }
}

@Preview @Composable private fun SectionLabelDarkPreview() = HabitlyTheme(darkTheme = true) { SectionLabelPreviewContent() }
@Preview @Composable private fun SectionLabelLightPreview() = HabitlyTheme(darkTheme = false) { SectionLabelPreviewContent() }
