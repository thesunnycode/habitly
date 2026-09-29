package com.habitly.app.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.Insight
import com.habitly.app.ui.components.HabitGrid
import com.habitly.app.ui.components.HeroCard
import com.habitly.app.ui.components.MiniBars
import com.habitly.app.ui.components.PeriodBars
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.Sparkline
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Each card says how much history it needs, so a thin dataset never renders as a broken chart. */
private const val DAYS_FOR_WEEKDAYS = 14
private const val DAYS_FOR_VERDICTS = 7

private val CARD_SHAPE = RoundedCornerShape(20.dp)

private const val YEAR_WEEKS = 52
private val YEAR_CELL = 8.dp
private val YEAR_GAP = 2.dp

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Clear of the floating nav bar.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!uiState.hasAnyHistory) {
            item { NothingYetCard() }
            return@LazyColumn
        }

        item { PeriodTabs(selected = uiState.period, onSelect = onPeriodChange) }

        // The insight leads, because a sentence about your own behaviour beats a chart of it.
        if (uiState.insights.isNotEmpty()) {
            item { InsightsCard(uiState.insights) }
        }

        item { CompletionHero(uiState) }

        item { RecordTiles(uiState) }

        item {
            if (uiState.daysOfHistory >= DAYS_FOR_WEEKDAYS) {
                WeekdayCard(uiState)
            } else {
                LockedCard(
                    title = "Your weekly pattern",
                    detail = "Which day of the week you tend to drop.",
                    daysNeeded = DAYS_FOR_WEEKDAYS,
                    daysSoFar = uiState.daysOfHistory,
                )
            }
        }

        item {
            if (uiState.daysOfHistory >= DAYS_FOR_VERDICTS) {
                VerdictCard(uiState)
            } else {
                LockedCard(
                    title = "Habit by habit",
                    detail = "Which habits are going well and which are slipping.",
                    daysNeeded = DAYS_FOR_VERDICTS,
                    daysSoFar = uiState.daysOfHistory,
                )
            }
        }
    }
}

/** Week / Month / Year as text tabs: the selected one in full ink over a brand underline. */
@Composable
private fun PeriodTabs(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        StatsPeriod.entries.forEach { period ->
            val active = period == selected
            Column(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(period) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = period.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(if (active) primary else Color.Transparent, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/** The standard Insights block: a surface card, outlined in light mode where there is no tonal step. */
@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHabitlyColors.current.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (title != null || subtitle != null) Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun InsightsCard(insights: List<Insight>) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "WHAT WE NOTICED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        insights.forEach { insight ->
            Text(
                text = insight.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * The focal point: completion for the period, its comparison, and the period itself — daily bars
 * for Week and Month, a year of all-habits activity for Year.
 */
@Composable
private fun CompletionHero(uiState: StatsUiState) {
    val percent = (uiState.completionRate * 100).toInt()
    val delta = uiState.delta?.takeIf { uiState.showDelta }
    val deltaPoints = delta?.let { (it * 100).toInt() }
    val period = uiState.period.label.lowercase()
    HeroCard(
        label = "Completion",
        value = "$percent%",
        caption = "of due days kept",
        // A zero change is neither good nor bad news, so it gets no arrow and no colour.
        delta = deltaPoints?.let {
            if (it == 0) "Same as previous $period" else "${if (it > 0) "+" else ""}$it% vs previous $period"
        },
        deltaPositive = deltaPoints?.takeIf { it != 0 }?.let { it > 0 },
    ) {
        Spacer(Modifier.height(16.dp))
        if (uiState.period == StatsPeriod.YEAR) {
            // Inset on a surface so the palest brand step never melts into the hero's own tint. A full
            // year at a readable cell size is wider than a phone, so it scrolls, opening on this week.
            val scroll = rememberScrollState()
            LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp)
                    .horizontalScroll(scroll),
            ) {
                HabitGrid(
                    levels = uiState.yearLevels,
                    colorHex = null,
                    weeks = YEAR_WEEKS,
                    cellGap = YEAR_GAP,
                    endDate = uiState.today,
                    modifier = Modifier.width(YEAR_CELL * YEAR_WEEKS + YEAR_GAP * (YEAR_WEEKS - 1)),
                    summary = { weeks -> "Activity across all habits for the last $weeks weeks" },
                )
            }
        } else {
            val bars = uiState.periodBars
            // Today is still running, so it is never called the weakest day.
            val past = bars.dropLast(1)
            val weakest = past.withIndex()
                .takeIf { past.size > 1 && past.min() < past.max() }
                ?.minByOrNull { it.value }?.index
            PeriodBars(
                values = bars,
                labels = uiState.periodBarLabels,
                weakestIndex = weakest,
                description = buildString {
                    append("Daily completion for the last ${bars.size} days, average ")
                    append("${(bars.average().takeIf { !it.isNaN() } ?: 0.0).times(100).toInt()}%")
                    if (weakest != null) append(", weakest day ${(bars[weakest] * 100).toInt()}%")
                },
            )
        }
    }
}

@Composable
private fun RecordTiles(uiState: StatsUiState) {
    val extras = LocalHabitlyColors.current
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = "${uiState.currentStreak}",
                label = "Current streak",
                icon = Icons.Filled.LocalFireDepartment,
                iconTint = extras.warning,
                animateFrom = 0,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${uiState.bestStreak}",
                label = "Best ever",
                icon = Icons.Filled.EmojiEvents,
                iconTint = extras.warning,
                animateFrom = 0,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${uiState.perfectDays}",
                label = "Perfect days",
                icon = Icons.Filled.Star,
                iconTint = MaterialTheme.colorScheme.primary,
                animateFrom = 0,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${uiState.totalCompletions}",
                label = "Total done",
                icon = Icons.Filled.CheckCircle,
                iconTint = extras.success,
                animateFrom = 0,
                modifier = Modifier.weight(1f),
            )
        }
        if (uiState.isPersonalBest && uiState.currentStreak >= 3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = extras.warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "This is your longest run so far.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekdayCard(uiState: StatsUiState) {
    val weekdays = DayOfWeek.entries
    val counts = weekdays.map { (uiState.completionsByWeekday[it] ?: 0).toFloat() }
    val weakIndex = uiState.weakestWeekday?.let { weekdays.indexOf(it) }

    StatsCard(
        title = "Your weekly pattern",
        subtitle = uiState.weakestWeekday?.let {
            "${it.getDisplayName(TextStyle.FULL, Locale.getDefault())} is your weakest day"
        } ?: "Completions on each weekday",
    ) {
        MiniBars(
            values = counts,
            labels = weekdays.map {
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
            },
            highlightIndex = weakIndex,
        )
    }
}

@Composable
private fun VerdictCard(uiState: StatsUiState) {
    StatsCard(title = "Habit by habit", subtitle = "Sorted by how they are going") {
        VerdictGroup.entries.forEach { group ->
            val rows = uiState.habitVerdicts.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            GroupLabel(group)
            rows.forEach { VerdictRow(it) }
        }
    }
}

@Composable
private fun GroupLabel(group: VerdictGroup) {
    val extras = LocalHabitlyColors.current
    val (text, dot) = when (group) {
        VerdictGroup.GOING_WELL -> "Going well" to extras.success
        VerdictGroup.SLIPPING -> "Slipping" to MaterialTheme.colorScheme.error
        VerdictGroup.NOT_ENOUGH_DATA -> "Not enough data" to extras.onSurfaceFaint
    }
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        SectionLabel(text = text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun VerdictRow(verdict: HabitVerdict) {
    val tones = habitTones(verdict.colorHex)
    val extras = LocalHabitlyColors.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(tones.full))
        Text(
            text = verdict.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Sparkline(
            values = verdict.spark,
            color = tones.full,
            modifier = Modifier.width(52.dp).height(20.dp),
        )
        Text(
            text = if (verdict.group == VerdictGroup.NOT_ENOUGH_DATA) {
                "—"
            } else {
                "${(verdict.rate * 100).toInt()}%"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
        // No arrow on a habit we cannot judge yet, nor on one that has not moved.
        verdict.delta?.takeIf {
            verdict.group != VerdictGroup.NOT_ENOUGH_DATA && (it * 100).toInt() != 0
        }?.let { delta ->
            Icon(
                imageVector = if (delta >= 0f) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (delta >= 0f) "Rising" else "Falling",
                tint = if (delta >= 0f) extras.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * A card that has not earned its data yet. It shows what it will become and when — the screen is
 * never broken, just partly not-yet-unlocked.
 */
@Composable
private fun LockedCard(title: String, detail: String, daysNeeded: Int, daysSoFar: Int) {
    StatsCard(
        title = title,
        subtitle = "$detail Unlocks on day $daysNeeded — $daysSoFar so far.",
    ) {
        MiniBars(
            values = listOf(0.5f, 0.8f, 0.35f, 0.65f, 0.9f, 0.45f, 0.7f),
            labels = List(7) { "" },
            barColor = MaterialTheme.colorScheme.outline,
            height = 72.dp,
        )
    }
}

@Composable
private fun NothingYetCard() {
    StatsCard(title = "No stats yet", subtitle = "Tick off a habit and this screen fills in") {
        Text(
            text = "Your completion rate, streak records and weekly pattern all appear here once " +
                "you have a few days of history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
