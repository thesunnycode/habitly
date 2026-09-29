package com.habitly.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitTones
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Pure calendar arithmetic for the grid components, kept out of composables so it can be unit tested. */
object GridMath {
    /** First day of each week column, oldest first; the last column contains [endDate]. */
    fun weekColumns(endDate: LocalDate, weeks: Int, weekStart: DayOfWeek): List<LocalDate> {
        val lastStart = endDate.with(TemporalAdjusters.previousOrSame(weekStart))
        return (weeks - 1 downTo 0).map { lastStart.minusWeeks(it.toLong()) }
    }

    /** Leading nulls, every day of [month], trailing nulls up to a multiple of 7. */
    fun monthCells(month: YearMonth, weekStart: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List(lead) { null } + days
        val trail = (7 - cells.size % 7) % 7
        return cells + List(trail) { null }
    }
}

/**
 * Contribution grid: 7 rows (days of the week) by [weeks] columns, sized to fill the available width
 * and painted on one Canvas so a screen full of cards stays cheap.
 * A null [colorHex] uses the brand tones.
 */
@Composable
fun HabitGrid(
    levels: Map<LocalDate, Int>,
    colorHex: String?,
    modifier: Modifier = Modifier,
    weeks: Int = 16,
    endDate: LocalDate = LocalDate.now(),
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    cellGap: Dp = 3.dp,
    summary: String,
) {
    val extras = LocalHabitlyColors.current
    val scheme = MaterialTheme.colorScheme
    val tones = if (colorHex != null) {
        habitTones(colorHex)
    } else {
        HabitTones(
            full = scheme.primary,
            mid = lerp(scheme.primaryContainer, scheme.primary, 0.5f),
            low = scheme.primaryContainer,
            onFull = scheme.onPrimary,
        )
    }
    val palette = remember(tones, extras.gridEmpty) {
        listOf(extras.gridEmpty, tones.low, tones.mid, lerp(tones.mid, tones.full, 0.5f), tones.full)
    }
    val columns = remember(endDate, weeks, weekStart) { GridMath.weekColumns(endDate, weeks, weekStart) }
    val todayBorder = scheme.onSurface

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cell = (maxWidth - cellGap * (weeks - 1)) / weeks
        val gridHeight = cell * 7 + cellGap * 6
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            val cellPx = cell.toPx()
            val gapPx = cellGap.toPx()
            val radius = CornerRadius(3.dp.toPx())
            val size = Size(cellPx, cellPx)
            columns.forEachIndexed { c, weekFirst ->
                for (r in 0 until 7) {
                    val date = weekFirst.plusDays(r.toLong())
                    if (date.isAfter(endDate)) continue
                    val topLeft = Offset(c * (cellPx + gapPx), r * (cellPx + gapPx))
                    val level = (levels[date] ?: 0).coerceIn(0, 4)
                    drawRoundRect(palette[level], topLeft, size, radius)
                    if (date == endDate) {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            todayBorder,
                            Offset(topLeft.x + stroke / 2, topLeft.y + stroke / 2),
                            Size(cellPx - stroke, cellPx - stroke),
                            radius,
                            style = Stroke(stroke),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitGridPreviewContent() {
    val end = LocalDate.of(2026, 9, 29)
    val levels = (0 until 112).associate { end.minusDays(it.toLong()) to (it * 7 % 5) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp)) {
            HabitGrid(levels, "#51CF66", endDate = end, summary = "Sample")
            HabitGrid(levels, null, endDate = end, summary = "Brand", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Preview @Composable private fun HabitGridDarkPreview() = HabitlyTheme(darkTheme = true) { HabitGridPreviewContent() }
@Preview @Composable private fun HabitGridLightPreview() = HabitlyTheme(darkTheme = false) { HabitGridPreviewContent() }
