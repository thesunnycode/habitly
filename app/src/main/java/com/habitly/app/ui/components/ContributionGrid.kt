package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val CELL = 13.dp
private val GAP = 3.dp

/** How many shading steps the grid uses, matching the familiar contribution-graph ramp. */
const val CONTRIBUTION_LEVELS = 4

/**
 * A GitHub/LeetCode-style contribution graph: one small square per day, seven weekday rows deep and
 * as many week columns wide as the range covers, with month labels along the top.
 *
 * This is deliberately not a month calendar. A calendar answers "what happened on the 14th"; a
 * contribution graph answers "have I been consistent for months", which is the question a habit
 * tracker exists to answer, and it is the view every reference app leads with.
 *
 * @param levels 0 means nothing recorded; 1..[CONTRIBUTION_LEVELS] shade progressively stronger.
 */
@Composable
fun ContributionGrid(
    levels: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    weeks: Int = 26,
    endDate: LocalDate = LocalDate.now(),
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // Columns are calendar weeks, so the weekday rows line up the way the familiar graph does.
    val lastColumnStart = endDate.with(DayOfWeek.MONDAY)
    val weekStarts = (weeks - 1 downTo 0).map { back -> lastColumnStart.minusWeeks(back.toLong()) }

    val scrollState = rememberScrollState()
    // Open on the present, not on ancient history.
    LaunchedEffect(weekStarts.size) { scrollState.scrollTo(scrollState.maxValue) }

    val recorded = levels.count { it.value > 0 }
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Activity graph: $recorded days recorded over the last $weeks weeks"
        },
    ) {
        WeekdayLabels()
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            MonthLabels(weekStarts)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                weekStarts.forEach { weekStart ->
                    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                        repeat(7) { dayOffset ->
                            val date = weekStart.plusDays(dayOffset.toLong())
                            DayCell(
                                level = levels[date] ?: 0,
                                isFuture = date.isAfter(endDate),
                                isToday = date == endDate,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayLabels() {
    // Every other row, as the familiar graph does — labelling all seven is unreadable at this size.
    val labels = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    Column(modifier = Modifier.padding(end = 6.dp, top = 20.dp)) {
        (0 until 7).forEach { row ->
            val day = DayOfWeek.of(row + 1)
            Box(
                modifier = Modifier.height(CELL).padding(bottom = 0.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (day in labels) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = with(LocalDensity.current) { 9.dp.toSp() },
                        lineHeight = with(LocalDensity.current) { 11.dp.toSp() },
                    )
                }
            }
            if (row < 6) Spacer(modifier = Modifier.height(GAP))
        }
    }
}

@Composable
private fun MonthLabels(weekStarts: List<LocalDate>) {
    Row {
        weekStarts.forEachIndexed { index, weekStart ->
            val previous = weekStarts.getOrNull(index - 1)
            val startsNewMonth = previous == null || previous.month != weekStart.month
            Box(modifier = Modifier.width(CELL + GAP)) {
                // Only the first column of each month is labelled, so the row reads as a timeline.
                if (startsNewMonth) {
                    Text(
                        text = weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = with(LocalDensity.current) { 9.dp.toSp() },
                        lineHeight = with(LocalDensity.current) { 12.dp.toSp() },
                        maxLines = 1,
                        softWrap = false,
                        // A month name is wider than one cell, so it is allowed to spill to the
                        // right of its column rather than wrap into "Ma / y".
                        modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(level: Int, isFuture: Boolean, isToday: Boolean, color: Color) {
    val shape = RoundedCornerShape(3.dp)
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    // The ramp walks lightness rather than alpha: an alpha ramp puts the faint steps below the 3:1
    // a meaningful graphic needs, and makes the colour depend on whatever is painted underneath.
    val fill = when {
        isFuture -> Color.Transparent
        level <= 0 -> empty
        else -> lerp(
            lerp(color, MaterialTheme.colorScheme.surface, 0.62f),
            color,
            (level - 1).toFloat() / (CONTRIBUTION_LEVELS - 1).coerceAtLeast(1),
        )
    }
    Box(
        modifier = Modifier
            .size(CELL)
            .background(fill, shape)
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape)
                    // Every cell keeps an outline, so a square is perceivable even when its fill is not.
                    !isFuture -> Modifier.border(
                        0.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape,
                    )
                    else -> Modifier
                }
            ),
    )
}

/** The "less → more" key that belongs under a contribution graph. */
@Composable
fun ContributionLegend(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        (0..CONTRIBUTION_LEVELS).forEach { level ->
            DayCell(level = level, isFuture = false, isToday = false, color = color)
            Spacer(modifier = Modifier.width(GAP))
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
