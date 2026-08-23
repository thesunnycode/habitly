package com.habitly.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.size(96.dp), contentAlignment = Alignment.Center) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = Stroke(width = 10f)
            drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = progressColor, startAngle = -90f, sweepAngle = 360f * clamped, useCenter = false, style = stroke)
        }
        Text(text = "${(clamped * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
    }
}
