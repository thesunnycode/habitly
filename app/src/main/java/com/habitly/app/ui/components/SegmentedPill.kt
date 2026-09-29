package com.habitly.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion

/** Pill-shaped single-choice control with a sliding selected segment. */
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHabitlyColors.current
    val outline = MaterialTheme.colorScheme.outline
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val padding = 4.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .padding(padding),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = if (LocalReduceMotion.current) snap() else spring(),
            label = "segmentOffset",
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = offset)
                .width(segmentWidth)
                .height(40.dp)
                .clip(CircleShape)
                .then(
                    if (colors.isDark) Modifier.background(outline)
                    else Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(1.dp, outline), CircleShape),
                ),
        )
        Row {
            options.forEach { option ->
                val isSelected = option == selected
                // 48dp hit box around the 40dp visual segment.
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(40.dp)
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Tab) { onSelect(option) }
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedPillPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        SegmentedPill(
            options = listOf("Week", "Month", "Year"),
            selected = "Month",
            onSelect = {},
            label = { it },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview @Composable private fun SegmentedPillDarkPreview() = HabitlyTheme(darkTheme = true) { SegmentedPillPreviewContent() }
@Preview @Composable private fun SegmentedPillLightPreview() = HabitlyTheme(darkTheme = false) { SegmentedPillPreviewContent() }
