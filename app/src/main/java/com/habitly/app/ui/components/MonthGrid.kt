package com.habitly.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A calendar month of square day cells with 4dp gaps. Colours are supplied by the caller; future days are inert.
 * Cell size is width-driven: about 50dp on a 411dp phone and about 42dp on a 360dp phone, so on narrow
 * phones the cells fall slightly short of the 48dp touch-target guideline (accepted trade-off for a 7-column month).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthGrid(
    month: YearMonth,
    cellFill: (LocalDate) -> Color,
    cellText: (LocalDate) -> Color,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    selected: LocalDate? = null,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    onDayClick: ((LocalDate) -> Unit)? = null,
    onDayLongClick: ((LocalDate) -> Unit)? = null,
    dayDescription: (LocalDate) -> String,
) {
    val cells = remember(month, weekStart) { GridMath.monthCells(month, weekStart) }
    val faint = LocalHabitlyColors.current.onSurfaceFaint
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val cellShape = RoundedCornerShape(10.dp)
    val selectedDay = selected
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 7).forEach { i ->
                val dow = weekStart.plus(i.toLong())
                Text(
                    text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val future = date.isAfter(today)
                        val border = when {
                            date == selected -> Modifier.border(2.dp, onSurface, cellShape)
                            date == today -> Modifier.border(1.5.dp, primary, cellShape)
                            else -> Modifier
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .alpha(if (future) 0.4f else 1f)
                                .clip(cellShape)
                                .background(cellFill(date))
                                .then(border)
                                .then(
                                    if (future || (onDayClick == null && onDayLongClick == null)) Modifier
                                    else Modifier.combinedClickable(
                                        role = Role.Button,
                                        onClick = { onDayClick?.invoke(date) },
                                        onLongClick = onDayLongClick?.let { { it(date) } },
                                    ),
                                )
                                .clearAndSetSemantics {
                                    contentDescription = dayDescription(date)
                                    this.selected = date == selectedDay
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cellText(date),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGridPreviewContent() {
    val today = LocalDate.of(2026, 9, 29)
    val extras = LocalHabitlyColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background) {
        Column(Modifier.padding(16.dp)) {
            MonthGrid(
                month = YearMonth.of(2026, 9),
                today = today,
                selected = LocalDate.of(2026, 9, 12),
                cellFill = { if (it.dayOfMonth % 3 == 0) scheme.primary else extras.gridEmpty },
                cellText = {
                    if (it.dayOfMonth % 3 == 0) scheme.onPrimary else scheme.onSurface
                },
                onDayClick = {},
                dayDescription = { it.toString() },
            )
        }
    }
}

@Preview @Composable private fun MonthGridDarkPreview() = HabitlyTheme(darkTheme = true) { MonthGridPreviewContent() }
@Preview @Composable private fun MonthGridLightPreview() = HabitlyTheme(darkTheme = false) { MonthGridPreviewContent() }
