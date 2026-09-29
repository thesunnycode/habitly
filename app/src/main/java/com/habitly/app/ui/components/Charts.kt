package com.habitly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion

/**
 * Rounded bars that spring up in sequence when the chart appears, each over an always-visible
 * track so a zero reads as "nothing yet" rather than as a missing bar. One bar can be tinted to
 * carry a point (the weakest weekday).
 */
@Composable
fun MiniBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    highlightColor: Color = MaterialTheme.colorScheme.error,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 132.dp,
) {
    val peak = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val trackColor = LocalHabitlyColors.current.gridEmpty

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                // Each bar arrives slightly after the one before it, so the chart reads left to right.
                val grown by animateFloatAsState(
                    targetValue = (value / peak).coerceIn(0f, 1f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "bar$index",
                )
                val color = if (index == highlightIndex) highlightColor else barColor
                Bar(
                    fraction = grown,
                    color = color,
                    trackColor = trackColor,
                    // weight() only distributes width in a Row; without fillMaxHeight the bar
                    // would wrap its empty content to zero height.
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (index == highlightIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == highlightIndex) {
                        highlightColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Bar(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val radius = CornerRadius(size.width / 2f)
                // A non-zero value always gets at least a pill's worth of height, so a small
                // count is still visible, but never more than the space available.
                val minimum = if (fraction > 0f) size.width.coerceAtMost(size.height) else 0f
                val fillHeight = (size.height * fraction).coerceIn(minimum, size.height)
                onDrawBehind {
                    drawRoundRect(color = trackColor, cornerRadius = radius)
                    if (fillHeight > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(0f, size.height - fillHeight),
                            size = Size(size.width, fillHeight),
                            cornerRadius = radius,
                        )
                    }
                }
            },
    )
}

/**
 * The hero's period chart: one bar per day on a 0..100% scale with a dashed average line. The
 * weakest bar is drawn in `error` so the chart carries a point, not just a shape.
 *
 * With one label per bar, labels sit under each bar; otherwise only the first and last are shown
 * at the chart's edges, which keeps a 30-bar month readable.
 */
@Composable
fun PeriodBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    weakestIndex: Int? = null,
    height: Dp = 112.dp,
    description: String,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val weakColor = MaterialTheme.colorScheme.error
    val trackColor = LocalHabitlyColors.current.gridEmpty
    val averageColor = LocalHabitlyColors.current.onSurfaceFaint
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val reduceMotion = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduceMotion) grow.animateTo(1f, tween(600)) }
    val average = if (values.isEmpty()) 0f else values.average().toFloat()
    val gap = if (values.size > 14) 3.dp else 8.dp

    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val gapPx = gap.toPx()
            val barWidth = (size.width - gapPx * (values.size - 1)) / values.size
            val radius = CornerRadius(6.dp.toPx().coerceAtMost(barWidth / 2f))
            values.forEachIndexed { i, value ->
                val x = i * (barWidth + gapPx)
                drawRoundRect(trackColor, Offset(x, 0f), Size(barWidth, size.height), radius)
                val h = size.height * value.coerceIn(0f, 1f) * grow.value
                if (h > 0f) {
                    drawRoundRect(
                        color = if (i == weakestIndex) weakColor else barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
            }
            val y = size.height * (1f - average.coerceIn(0f, 1f))
            drawLine(
                color = averageColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (labels.size == values.size && values.size <= 14) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                labels.forEachIndexed { i, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (i == weakestIndex) weakColor else muted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = muted)
                Box(Modifier.weight(1f))
                Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/**
 * A tiny shape-only trend drawn behind a list row. No axes, no labels — at this size it exists to
 * say "rising" or "falling" at a glance, and anything else would be noise.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.size < 2) {
        Box(modifier = modifier)
        return
    }
    val peak = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(x = stepX * index, y = size.height - size.height * (value / peak))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            // Cubic control points at the horizontal midpoint give a smooth curve without
            // overshooting into negative territory the way a spline would.
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val midX = (a.x + b.x) / 2f
                cubicTo(midX, a.y, midX, b.y, b.x, b.y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
