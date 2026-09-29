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
import kotlin.math.floor

/** Pure calendar arithmetic for the grid components, kept out of composables so it can be unit tested. */
object GridMath {
    /** First day of each week column, oldest first; the last column contains [endDate]. */
    fun weekColumns(endDate: LocalDate, weeks: Int, weekStart: DayOfWeek): List<LocalDate> {
        val lastStart = endDate.with(TemporalAdjusters.previousOrSame(weekStart))
        return (weeks - 1 downTo 0).map { lastStart.minusWeeks(it.toLong()) }
    }

    /**
     * How many week columns of [cellSize] fit in [availableWidth] with [gap] between them (all in the
     * same unit): n cells need n * cell + (n - 1) * gap. Never less than one.
     */
    fun weeksThatFit(availableWidth: Float, cellSize: Float, gap: Float): Int =
        // The small epsilon keeps an exact fit from flooring one short through float rounding.
        maxOf(1, floor((availableWidth + gap) / (cellSize + gap) + 1e-4f).toInt())

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
 * Contribution grid: 7 rows (days of the week) by N week columns ending at [endDate], painted on one
 * Canvas so a screen full of cards stays cheap. A null [colorHex] uses the brand tones.
 *
 * With [cellSize] set, cells are drawn at exactly that size and as many weeks as fit the width are
 * shown, right-aligned so the newest week sits at the right edge. With [cellSize] null, [weeks]
 * columns are stretched to fill the width. [summary] receives the number of weeks actually shown.
 */
@Composable
fun HabitGrid(
    levels: Map<LocalDate, Int>,
    colorHex: String?,
    modifier: Modifier = Modifier,
    weeks: Int = 16,
    cellSize: Dp? = null,
    endDate: LocalDate = LocalDate.now(),
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    cellGap: Dp = 3.dp,
    summary: (weeksShown: Int) -> String,
) {
    val extras = LocalHabitlyColors.current
    val scheme = MaterialTheme.colorScheme
    val tones = if (colorHex != null) {
        habitTones(colorHex)
    } else {
        HabitTones(
            full = scheme.primary,
            // Same ramp as the Insights calendar: part-way to primary, so the lowest level stays
            // distinct from an empty cell in light theme.
            mid = lerp(scheme.primaryContainer, scheme.primary, 0.55f),
            low = lerp(scheme.primaryContainer, scheme.primary, 0.30f),
            onFull = scheme.onPrimary,
        )
    }
    val palette = remember(tones, extras.gridEmpty) {
        listOf(extras.gridEmpty, tones.low, tones.mid, lerp(tones.mid, tones.full, 0.5f), tones.full)
    }
    val todayBorder = scheme.onSurface

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val shownWeeks = if (cellSize != null) {
            GridMath.weeksThatFit(maxWidth.value, cellSize.value, cellGap.value)
        } else {
            weeks
        }
        val cell = cellSize ?: ((maxWidth - cellGap * (shownWeeks - 1)) / shownWeeks)
        val gridWidth = cell * shownWeeks + cellGap * (shownWeeks - 1)
        val gridHeight = cell * 7 + cellGap * 6
        val columns = remember(endDate, shownWeeks, weekStart) { GridMath.weekColumns(endDate, shownWeeks, weekStart) }
        val description = summary(shownWeeks)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            val cellPx = cell.toPx()
            val gapPx = cellGap.toPx()
            // Right-align: any width left over after whole cells goes on the left edge.
            val startX = (size.width - gridWidth.toPx()).coerceAtLeast(0f)
            val radius = CornerRadius(3.dp.toPx())
            val cellRect = Size(cellPx, cellPx)
            columns.forEachIndexed { c, weekFirst ->
                for (r in 0 until 7) {
                    val date = weekFirst.plusDays(r.toLong())
                    if (date.isAfter(endDate)) continue
                    val topLeft = Offset(startX + c * (cellPx + gapPx), r * (cellPx + gapPx))
                    val level = (levels[date] ?: 0).coerceIn(0, 4)
                    drawRoundRect(palette[level], topLeft, cellRect, radius)
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
            HabitGrid(levels, "#51CF66", endDate = end, summary = { "Sample" })
            HabitGrid(levels, null, endDate = end, cellSize = 10.dp, summary = { "Brand" }, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Preview @Composable private fun HabitGridDarkPreview() = HabitlyTheme(darkTheme = true) { HabitGridPreviewContent() }
@Preview @Composable private fun HabitGridLightPreview() = HabitlyTheme(darkTheme = false) { HabitGridPreviewContent() }
