package com.habitly.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors

/** A thin progress ring with a centred label ("3/4"); [description] is what screen readers hear. */
@Composable
fun ProgressRing(
    progress: Float,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 6.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = LocalHabitlyColors.current.gridEmpty
    val progressColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            // Inset by half the stroke so the ring sits inside its bounds rather than being clipped.
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawArc(trackColor, -90f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (clamped > 0f) {
                drawArc(
                    progressColor, -90f, 360f * clamped, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = stroke,
                )
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProgressRingPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(16.dp)) {
            ProgressRing(0f, "0/4", "0 of 4 done")
            ProgressRing(0.75f, "3/4", "3 of 4 done", Modifier.padding(start = 12.dp))
            ProgressRing(1f, "4/4", "4 of 4 done", Modifier.padding(start = 12.dp))
        }
    }
}

@Preview @Composable private fun ProgressRingDarkPreview() = HabitlyTheme(darkTheme = true) { ProgressRingPreviewContent() }
@Preview @Composable private fun ProgressRingLightPreview() = HabitlyTheme(darkTheme = false) { ProgressRingPreviewContent() }
