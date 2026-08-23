package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.domain.CompletionRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Monday-first weekday initials for the grid header. */
private val WEEKDAY_INITIALS = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
).map { it.name.take(1) }

/**
 * A month grid where each completed day is filled with the habit's colour. Reads as a calendar
 * first (weekday header, day numbers, real week rows) and a heatmap second.
 */
@Composable
fun CalendarHeatmap(
    records: List<CompletionRecord>,
    month: YearMonth,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    today: LocalDate = LocalDate.now(),
) {
    val completedDates = records.filter { it.completed }.map { it.date }.toSet()
    // Monday-first: DayOfWeek.value is 1 (Mon) through 7 (Sun).
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1

    val cells = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WEEKDAY_INITIALS.forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        completed = date != null && date in completedDates,
                        isToday = date == today,
                        isFuture = date != null && date.isAfter(today),
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    completed: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    // The empty state must not be surfaceVariant: these grids sit on a surfaceVariant card,
    // where that colour makes unfilled days disappear entirely.
    val background = when {
        date == null -> Color.Transparent
        completed -> color
        isFuture -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(background, shape)
            .then(
                if (isToday && !completed) Modifier.border(1.5.dp, color, shape) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(
                text = "${date.dayOfMonth}",
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    completed -> Color.White
                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
