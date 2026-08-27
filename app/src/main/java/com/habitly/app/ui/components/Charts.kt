package com.habitly.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.domain.CompletionStatus

/**
 * Rounded, gradient-filled bars that spring up in sequence when the chart appears.
 *
 * Replaces a flat `drawRect` chart. The differences that matter: pill caps, a vertical gradient so a
 * bar has depth, an always-visible track behind each bar so a zero reads as "nothing yet" rather
 * than as a missing bar, and one bar that can be tinted to carry a point (the weakest weekday).
 */
@Composable
fun MiniBars(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    highlightColor: Color = MaterialTheme.colorScheme.error,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 132.dp,
) {
    val peak = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

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
                    // weight() only distributes width in a Row. Without fillMaxHeight the Box
                    // wrapped its (empty) content to zero height, so every bar collapsed into a
                    // black circle: the gradient was being asked for a zero-height span.
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
            .then(
                Modifier.drawWithCache {
                    val radius = CornerRadius(size.width / 2f)
                    // A non-zero value always gets at least a pill's worth of height, so a small
                    // count is still visible, but never more than the space available.
                    val minimum = if (fraction > 0f) size.width.coerceAtMost(size.height) else 0f
                    val fillHeight = (size.height * fraction).coerceIn(minimum, size.height)
                    val brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.72f)),
                        startY = size.height - fillHeight,
                        endY = size.height,
                    )
                    onDrawBehind {
                        // The track keeps every slot visible, so an empty day is a shape, not a gap.
                        drawRoundRect(color = trackColor, cornerRadius = radius)
                        if (fillHeight > 0f) {
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(0f, size.height - fillHeight),
                                size = Size(size.width, fillHeight),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
            ),
    )
}

/**
 * A week as seven pills.
 *
 * This is what replaced the 7-point completion-rate line chart. With seven binary days a line can
 * only take eight values, so it drew a staircase and called it a trend; pills say the same thing
 * without pretending to be continuous, and they carry the rest-day and miss states too.
 */
@Composable
fun DayPills(
    statuses: List<CompletionStatus?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    todayIndex: Int? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statuses.forEachIndexed { index, status ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fill = when (status) {
                    CompletionStatus.DONE -> color
                    CompletionStatus.PARTIAL -> color.copy(alpha = 0.45f)
                    CompletionStatus.SKIPPED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .then(
                            // Today gets an outline, so the row can be read as a position in time
                            // rather than seven interchangeable circles.
                            if (index == todayIndex) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    CircleShape,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (status) {
                        CompletionStatus.DONE -> Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(20.dp),
                        )
                        CompletionStatus.SKIPPED -> Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        else -> Unit
                    }
                }
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == todayIndex) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
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

/** A percentage rendered large enough to be the thing you read first. */
@Composable
fun HeroPercent(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "hero",
    )
    Text(
        text = "${(animated * 100).toInt()}%",
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier,
    )
}
