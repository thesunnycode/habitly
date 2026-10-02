package com.habitly.app.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitGrid
import com.habitly.app.ui.theme.HabitTones
import com.habitly.app.ui.theme.HabitTonesFactory
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Pure layout arithmetic for the Stats hero grid, kept out of composables so it can be unit tested. */
internal object StatsGridMath {
    /**
     * The window's days as rows of seven. Aligned (Month): whole weeks starting on [weekStart], with
     * blanks (null) for days outside the window. Not aligned (the rolling Week): the days as they
     * come, oldest first, so seven days are exactly one row.
     */
    fun weekRows(window: List<LocalDate>, weekStart: DayOfWeek, alignToWeeks: Boolean): List<List<LocalDate?>> {
        if (window.isEmpty()) return emptyList()
        val cells: List<LocalDate?> = if (alignToWeeks) {
            val lead = (window.first().dayOfWeek.value - weekStart.value + 7) % 7
            List(lead) { null } + window
        } else {
            window
        }
        val trail = (7 - cells.size % 7) % 7
        return (cells + List(trail) { null }).chunked(7)
    }

    /** The weekday above each of the seven columns. */
    fun headerDays(window: List<LocalDate>, weekStart: DayOfWeek, alignToWeeks: Boolean): List<DayOfWeek> =
        if (alignToWeeks || window.isEmpty()) {
            (0 until 7).map { weekStart.plus(it.toLong()) }
        } else {
            (0 until 7).map { window.first().plusDays(it.toLong()).dayOfWeek }
        }

    /**
     * Bottom-first drawing order of a day's slices: kept habits in chip order, then rest days on top,
     * so the solid colour always rises from the bottom like a fill level.
     */
    fun stackOrder(slices: List<DaySlice>): List<DaySlice> =
        slices.filter { !it.rested } + slices.filter { it.rested }

    /**
     * (top, bottom) of the first [count] of [total] equal slots in a cell of [height], bottom slot
     * first. One slot per active habit, so 1 of 4 kept fills a quarter and 4 of 4 fills the cell.
     */
    fun sliceBands(count: Int, total: Int, height: Float, gap: Float): List<Pair<Float, Float>> {
        if (count <= 0 || total <= 0) return emptyList()
        val slice = (height - gap * (total - 1)) / total
        return (0 until count.coerceAtMost(total)).map { i ->
            val bottom = height - i * (slice + gap)
            (bottom - slice) to bottom
        }
    }

    private fun dayLabel(day: LocalDate, locale: Locale) =
        "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, locale)}"

    /** A Week cell read aloud: "24 September: Water, Read done; Run rested". */
    fun allDayDescription(day: LocalDate, slices: List<DaySlice>, locale: Locale = Locale.getDefault()): String {
        val done = slices.filter { !it.rested }.map { it.name }
        val rested = slices.filter { it.rested }.map { it.name }
        val parts = buildList {
            if (done.isNotEmpty()) add(done.joinToString(", ") + " done")
            if (rested.isNotEmpty()) add(rested.joinToString(", ") + " rested")
        }
        return "${dayLabel(day, locale)}: " + (parts.joinToString("; ").ifEmpty { "nothing done" })
    }

    fun habitDayDescription(day: LocalDate, level: Int, beforeStart: Boolean, locale: Locale = Locale.getDefault()): String {
        val what = when {
            beforeStart -> "before this habit started"
            level >= 4 -> "done"
            level >= 2 -> "partly done"
            level == 1 -> "rested"
            else -> "not done"
        }
        return "${dayLabel(day, locale)}: $what"
    }

    fun allSummary(window: List<LocalDate>, stacks: Map<LocalDate, List<DaySlice>>): String {
        val kept = window.count { day -> stacks[day].orEmpty().any { !it.rested } }
        return "Last ${window.size} days: $kept of ${window.size} days with a habit done"
    }

    fun habitSummary(name: String, window: List<LocalDate>, levels: Map<LocalDate, Int>): String {
        val kept = window.count { (levels[it] ?: 0) >= 4 }
        return "$name, last ${window.size} days: done on $kept of ${window.size} days"
    }
}

/** Everything the grid draws, so a change of filter or period can crossfade between two whole grids. */
@Immutable
internal data class GridModel(
    val period: StatsPeriod,
    /** The selected habit's colour, or null for all habits. */
    val colorHex: String?,
    val habitName: String?,
    val window: List<LocalDate>,
    val stacks: Map<LocalDate, List<DaySlice>>,
    val levels: Map<LocalDate, Int>,
    val today: LocalDate,
    /** One slot per active habit: the height of a full stack. */
    val slotCount: Int,
    /** The selected habit's first day; earlier days are drawn faint. */
    val habitStart: LocalDate?,
)

internal fun StatsUiState.gridModel(): GridModel {
    // Built from what the data describes, never from a selection the data has not caught up with.
    val chip = chips.firstOrNull { it.id == dataHabitId }
    return GridModel(
        period = dataPeriod,
        colorHex = chip?.colorHex,
        habitName = chip?.name,
        window = window,
        stacks = if (chip == null) dayStacks else emptyMap(),
        levels = when {
            chip != null -> habitLevels
            dataPeriod == StatsPeriod.YEAR -> yearLevels
            else -> emptyMap()
        },
        today = today,
        slotCount = chips.size,
        habitStart = if (chip != null) selectedHabitStart else null,
    )
}

private val WINDOW_GAP = 4.dp
private val WINDOW_RADIUS = 6.dp
private val SLICE_GAP = 1.5.dp
private const val YEAR_WEEKS = 52
private val YEAR_CELL = 8.dp
private val YEAR_GAP = 2.dp

/**
 * The Stats hero: which slice of history this is, the headline rate with its change, the grid of
 * the window's days, and one line of insight.
 */
@Composable
internal fun StatsHeroCard(uiState: StatsUiState, weekStart: DayOfWeek, modifier: Modifier = Modifier) {
    val model = uiState.gridModel()
    val reduceMotion = LocalReduceMotion.current
    val scope = model.habitName ?: "All habits"
    val span = when (model.period) {
        StatsPeriod.WEEK -> "Last 7 days"
        StatsPeriod.MONTH -> "Last 30 days"
        StatsPeriod.YEAR -> "Last 52 weeks"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHabitlyColors.current.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .then(if (reduceMotion) Modifier else Modifier.animateContentSize(tween(200)))
                .padding(16.dp),
        ) {
            Text(
                text = "$scope · $span",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(uiState.completionRate * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "of due days kept",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.showDelta) {
                uiState.delta?.let { DeltaChip(it, model.period, Modifier.padding(top = 10.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Crossfade(
                targetState = model,
                animationSpec = if (reduceMotion) snap() else tween(200),
                label = "statsGrid",
            ) { shown ->
                if (shown.period == StatsPeriod.YEAR) {
                    YearGrid(shown, weekStart)
                } else {
                    WindowGrid(shown, weekStart)
                }
            }
            uiState.insights.firstOrNull()?.let { insight ->
                Text(
                    text = insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/** The change against the previous period, as a tinted pill. No change is neither good nor bad news. */
@Composable
private fun DeltaChip(delta: Float, period: StatsPeriod, modifier: Modifier = Modifier) {
    val points = (delta * 100).toInt()
    val versus = when (period) {
        StatsPeriod.WEEK -> "previous 7 days"
        StatsPeriod.MONTH -> "previous 30 days"
        StatsPeriod.YEAR -> "previous year"
    }
    if (points == 0) {
        Text(
            text = "Same as before",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val up = points > 0
    val tint = if (up) LocalHabitlyColors.current.success else MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "${if (up) "+" else ""}$points% vs $versus",
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Week and Month: one square per day of the window, painted on a single Canvas. All habits: each day
 * is a stack of equal slots, one per active habit, filled bottom-up by the habits kept (full tone)
 * then rested (low tone); the rest of the cell stays empty. One habit: the day in that habit's
 * tones, with days before the habit existed drawn faint.
 */
@Composable
internal fun WindowGrid(model: GridModel, weekStart: DayOfWeek) {
    val extras = LocalHabitlyColors.current
    val aligned = model.period == StatsPeriod.MONTH
    val rows = remember(model.window, weekStart, aligned) { StatsGridMath.weekRows(model.window, weekStart, aligned) }
    val header = remember(model.window, weekStart, aligned) { StatsGridMath.headerDays(model.window, weekStart, aligned) }
    val habitPalette = model.colorHex?.let { levelPalette(habitTones(it), extras.gridEmpty) }
    val sliceTones: Map<String, HabitTones> = remember(model.stacks, extras.isDark) {
        model.stacks.values.flatten().map { it.colorHex }.distinct()
            .associateWith { HabitTonesFactory.of(it, extras.isDark) }
    }
    val faint = extras.gridEmpty.copy(alpha = 0.4f)
    val todayRing = MaterialTheme.colorScheme.onSurface
    // One path, reset for each stacked cell, rather than a fresh allocation per cell per frame.
    val clip = remember { Path() }
    // Week cells are big enough to be read one by one; Month gets one summary instead.
    val perCell = model.period == StatsPeriod.WEEK
    val summary = if (model.colorHex != null) {
        StatsGridMath.habitSummary(model.habitName.orEmpty(), model.window, model.levels)
    } else {
        StatsGridMath.allSummary(model.window, model.stacks)
    }
    val habitStart = model.habitStart
    fun beforeStart(date: LocalDate) = habitStart != null && date.isBefore(habitStart)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(WINDOW_GAP),
        ) {
            header.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.onSurfaceFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - WINDOW_GAP * 6) / 7
            val height = cell * rows.size + WINDOW_GAP * (rows.size - 1).coerceAtLeast(0)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clearAndSetSemantics { if (!perCell) contentDescription = summary },
            ) {
                val gapPx = WINDOW_GAP.toPx()
                val cellPx = (size.width - gapPx * 6) / 7
                val radius = CornerRadius(WINDOW_RADIUS.toPx())
                val cellSize = Size(cellPx, cellPx)
                val sliceGap = SLICE_GAP.toPx()
                rows.forEachIndexed { r, row ->
                    row.forEachIndexed { c, date ->
                        if (date == null) return@forEachIndexed
                        val topLeft = Offset(c * (cellPx + gapPx), r * (cellPx + gapPx))
                        if (habitPalette != null && beforeStart(date)) {
                            // Before the habit existed: a faint fill with a hairline, so it reads as
                            // "not part of this habit's life" rather than as a missed day.
                            val hairline = 1.dp.toPx()
                            drawRoundRect(faint, topLeft, cellSize, radius)
                            drawRoundRect(
                                color = extras.gridEmpty,
                                topLeft = Offset(topLeft.x + hairline / 2, topLeft.y + hairline / 2),
                                size = Size(cellPx - hairline, cellPx - hairline),
                                cornerRadius = radius,
                                style = Stroke(hairline),
                            )
                        } else if (habitPalette != null) {
                            drawRoundRect(habitPalette[(model.levels[date] ?: 0).coerceIn(0, 4)], topLeft, cellSize, radius)
                        } else {
                            drawRoundRect(extras.gridEmpty, topLeft, cellSize, radius)
                            val slices = StatsGridMath.stackOrder(model.stacks[date].orEmpty())
                            val bands = StatsGridMath.sliceBands(slices.size, model.slotCount, cellPx, sliceGap)
                            if (bands.isNotEmpty()) {
                                clip.reset()
                                clip.addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + cellPx, topLeft.y + cellPx, radius))
                                clipPath(clip) {
                                    slices.zip(bands).forEach { (slice, band) ->
                                        val tones = sliceTones.getValue(slice.colorHex)
                                        drawRect(
                                            color = if (slice.rested) tones.low else tones.full,
                                            topLeft = Offset(topLeft.x, topLeft.y + band.first),
                                            size = Size(cellPx, band.second - band.first),
                                        )
                                    }
                                }
                            }
                        }
                        if (date == model.today) {
                            val stroke = 1.5.dp.toPx()
                            drawRoundRect(
                                color = todayRing,
                                topLeft = Offset(topLeft.x + stroke / 2, topLeft.y + stroke / 2),
                                size = Size(cellPx - stroke, cellPx - stroke),
                                cornerRadius = radius,
                                style = Stroke(stroke),
                            )
                        }
                    }
                }
            }
            if (perCell) {
                // Transparent stand-ins over the painted row, so each day is its own screen-reader stop.
                Row(Modifier.fillMaxWidth().height(cell), horizontalArrangement = Arrangement.spacedBy(WINDOW_GAP)) {
                    rows.firstOrNull().orEmpty().forEach { date ->
                        val description = when {
                            date == null -> null
                            model.colorHex != null -> StatsGridMath.habitDayDescription(
                                date,
                                model.levels[date] ?: 0,
                                beforeStart(date),
                            )
                            else -> StatsGridMath.allDayDescription(
                                date,
                                StatsGridMath.stackOrder(model.stacks[date].orEmpty()),
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (description != null) {
                                        Modifier.clearAndSetSemantics { contentDescription = description }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Levels 0..4 in a habit's tones, the same ramp [HabitGrid] uses. */
private fun levelPalette(tones: HabitTones, empty: Color) =
    listOf(empty, tones.low, tones.mid, lerp(tones.mid, tones.full, 0.5f), tones.full)

/**
 * Year: 52 weeks is wider than a phone at a readable cell size, so it scrolls, opening on this
 * week. Stacks are unreadable at 8dp, so all habits use the brand intensity ramp.
 */
@Composable
private fun YearGrid(model: GridModel, weekStart: DayOfWeek) {
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        HabitGrid(
            levels = model.levels,
            colorHex = model.colorHex,
            weeks = YEAR_WEEKS,
            cellGap = YEAR_GAP,
            endDate = model.today,
            weekStart = weekStart,
            modifier = Modifier.width(YEAR_CELL * YEAR_WEEKS + YEAR_GAP * (YEAR_WEEKS - 1)),
            summary = { weeks ->
                if (model.habitName != null) {
                    val kept = model.levels.count { (day, level) ->
                        level >= 4 && !day.isBefore(model.today.minusWeeks(weeks.toLong()))
                    }
                    "${model.habitName}, last $weeks weeks: done on $kept days"
                } else {
                    "Activity across all habits for the last $weeks weeks"
                }
            },
        )
    }
}
