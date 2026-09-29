package com.habitly.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalHabitlyColors
import androidx.compose.ui.unit.sp
import com.habitly.app.domain.Insight
import com.habitly.app.ui.components.DayPills
import com.habitly.app.ui.components.MiniBars
import com.habitly.app.ui.components.MiniStatTile
import com.habitly.app.ui.components.OutlinedSectionCard
import com.habitly.app.ui.components.ProgressArc
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.Sparkline
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Each card says how much history it needs, so a thin dataset never renders as a broken chart. */
private const val DAYS_FOR_WEEKDAYS = 14
private const val DAYS_FOR_VERDICTS = 7

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Stats",
                subtitle = if (uiState.hasAnyHistory) {
                    "${uiState.daysOfHistory} days of history"
                } else {
                    "Nothing tracked yet"
                },
            )
        }

        if (!uiState.hasAnyHistory) {
            item { NothingYetCard() }
            return@LazyColumn
        }

        // The insight leads, because a sentence about your own behaviour beats a chart of it.
        if (uiState.insights.isNotEmpty()) {
            item { InsightsCard(uiState.insights) }
        }

        // The one dark block on the screen. Everything below it is deliberately quieter.
        item { HeroBlock(uiState = uiState, onPeriodChange = onPeriodChange) }

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

@Composable
private fun InsightsCard(insights: List<Insight>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Intrinsic height so the accent edge measures itself against the text beside it,
            // rather than being given a guessed fixed height.
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        // An accent edge rather than another full grey box, so this reads as a note, not a chart.
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "WHAT WE NOTICED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            insights.forEach { insight ->
                Text(
                    text = insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * The focal point: a dark gradient block carrying the headline number, its comparison, an arc, and
 * the week. Previously all of this sat in the same pale grey card as everything else, which is what
 * made the screen read as a stack of slabs with nothing to look at first.
 */
@Composable
private fun HeroBlock(uiState: StatsUiState, onPeriodChange: (StatsPeriod) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(LocalHabitlyColors.current.heroContainer, LocalHabitlyColors.current.heroContainer)))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "COMPLETION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
            PeriodPills(selected = uiState.period, onSelect = onPeriodChange)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${(uiState.completionRate * 100).toInt()}%",
                    fontSize = 60.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "of due days kept",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                if (uiState.showDelta) {
                    DeltaChip(
                        delta = uiState.delta ?: 0f,
                        basis = "vs previous ${uiState.period.label.lowercase()}",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            ProgressArc(
                progress = uiState.completionRate,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(96.dp),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "LAST 7 DAYS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        DayPills(
            statuses = uiState.weekStatuses,
            labels = uiState.weekLabels,
            todayIndex = uiState.todayIndex,
            color = Color.White,
            onSurface = Color.White,
            checkColor = LocalHabitlyColors.current.heroContainer,
            size = 34.dp,
        )
    }
}

/** Compact pills rather than a full segmented control — less chrome on a dark surface. */
@Composable
private fun PeriodPills(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StatsPeriod.entries.forEach { period ->
            val active = period == selected
            Text(
                text = period.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) LocalHabitlyColors.current.heroContainer else Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** A delta is never shown bare — the thing it is measured against travels with it. */
@Composable
private fun DeltaChip(delta: Float, basis: String, modifier: Modifier = Modifier) {
    val rising = delta >= 0f
    val tint = if (rising) Color.White else Color(0xFFFFC4BC)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (rising) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "${if (rising) "+" else ""}${(delta * 100).toInt()}%  $basis",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun RecordTiles(uiState: StatsUiState) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStatTile(
                icon = Icons.Filled.LocalFireDepartment,
                label = "Current\nstreak",
                value = "${uiState.currentStreak}",
                accent = LocalHabitlyColors.current.warning,
                modifier = Modifier.weight(1f),
            )
            MiniStatTile(
                icon = Icons.Filled.EmojiEvents,
                label = "Best\never",
                value = "${uiState.bestStreak}",
                accent = LocalHabitlyColors.current.warning,
                modifier = Modifier.weight(1f),
            )
            MiniStatTile(
                icon = Icons.Filled.Star,
                label = "Perfect\ndays",
                value = "${uiState.perfectDays}",
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            MiniStatTile(
                icon = Icons.Filled.CheckCircle,
                label = "Total\ndone",
                value = "${uiState.totalCompletions}",
                accent = MaterialTheme.colorScheme.primary,
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
                    tint = LocalHabitlyColors.current.warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "This is your longest run so far.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
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

    OutlinedSectionCard(
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
    OutlinedSectionCard(title = "Habit by habit", subtitle = "Sorted by how they are going") {
        VerdictGroup.entries.forEach { group ->
            val rows = uiState.habitVerdicts.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            GroupChip(group)
            rows.forEach { VerdictRow(it) }
        }
    }
}

@Composable
private fun GroupChip(group: VerdictGroup) {
    val (text, tint) = when (group) {
        VerdictGroup.GOING_WELL -> "GOING WELL" to MaterialTheme.colorScheme.primary
        VerdictGroup.SLIPPING -> "SLIPPING" to MaterialTheme.colorScheme.error
        VerdictGroup.NOT_ENOUGH_DATA -> "NOT ENOUGH DATA" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = tint,
        modifier = Modifier
            .padding(top = 14.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun VerdictRow(verdict: HabitVerdict) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(verdict.colorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            text = verdict.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Sparkline(
            values = verdict.spark,
            color = color,
            modifier = Modifier.width(52.dp).height(20.dp),
        )
        Text(
            text = if (verdict.group == VerdictGroup.NOT_ENOUGH_DATA) {
                "—"
            } else {
                "${(verdict.rate * 100).toInt()}%"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )
        // No arrow on a habit we have just said we cannot judge yet.
        verdict.delta?.takeIf { verdict.group != VerdictGroup.NOT_ENOUGH_DATA }?.let { delta ->
            Icon(
                imageVector = if (delta >= 0f) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (delta >= 0f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * A card that has not earned its data yet. It shows what it will become and when, following Apple's
 * "Needs More Data" bucket — the screen is never broken, just partly not-yet-unlocked.
 */
@Composable
private fun LockedCard(title: String, detail: String, daysNeeded: Int, daysSoFar: Int) {
    OutlinedSectionCard(
        title = title,
        subtitle = "$detail Unlocks on day $daysNeeded — $daysSoFar so far.",
    ) {
        MiniBars(
            values = listOf(0.5f, 0.8f, 0.35f, 0.65f, 0.9f, 0.45f, 0.7f),
            labels = List(7) { "" },
            barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            height = 72.dp,
        )
    }
}

@Composable
private fun NothingYetCard() {
    OutlinedSectionCard(title = "No stats yet", subtitle = "Tick off a habit and this screen fills in") {
        Text(
            text = "Your completion rate, streak records and weekly pattern all appear here once " +
                "you have a few days of history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
