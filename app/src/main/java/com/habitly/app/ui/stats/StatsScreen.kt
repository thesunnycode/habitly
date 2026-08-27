package com.habitly.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.Insight
import com.habitly.app.ui.components.DayPills
import com.habitly.app.ui.components.HeroPercent
import com.habitly.app.ui.components.MiniBars
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

        // The insight block leads, because a sentence about your own behaviour beats a chart of it.
        if (uiState.insights.isNotEmpty()) {
            item { InsightsCard(uiState.insights) }
        }

        item {
            HeroCard(uiState = uiState, onPeriodChange = onPeriodChange)
        }

        item { RecordsCard(uiState) }

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "WHAT WE NOTICED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            insights.forEach { insight ->
                Text(
                    text = insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(uiState: StatsUiState, onPeriodChange: (StatsPeriod) -> Unit) {
    SectionCard(title = "Completion", subtitle = "Share of due days you kept") {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = uiState.period == period,
                    onClick = { onPeriodChange(period) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size),
                    label = { Text(period.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            HeroPercent(value = uiState.completionRate)
            if (uiState.showDelta) {
                DeltaChip(
                    delta = uiState.delta ?: 0f,
                    basis = "vs previous ${uiState.period.label.lowercase()}",
                    modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "LAST 7 DAYS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        DayPills(
            statuses = uiState.weekStatuses,
            labels = uiState.weekLabels,
            todayIndex = uiState.todayIndex,
        )
    }
}

/** A delta is never shown bare — the thing it is measured against travels with it. */
@Composable
private fun DeltaChip(delta: Float, basis: String, modifier: Modifier = Modifier) {
    val rising = delta >= 0f
    val tint = if (rising) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (rising) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "${if (rising) "+" else ""}${(delta * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
        Text(
            text = basis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp),
        )
    }
}

@Composable
private fun RecordsCard(uiState: StatsUiState) {
    SectionCard(title = "Records") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecordValue("Current streak", "${uiState.currentStreak}", Modifier.weight(1f))
            RecordValue("Best ever", "${uiState.bestStreak}", Modifier.weight(1f))
            RecordValue("Perfect days", "${uiState.perfectDays}", Modifier.weight(1f))
            RecordValue("Completions", "${uiState.totalCompletions}", Modifier.weight(1f))
        }
        if (uiState.isPersonalBest && uiState.currentStreak >= 3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "This is your longest run so far.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekdayCard(uiState: StatsUiState) {
    val weekdays = DayOfWeek.entries
    val counts = weekdays.map { (uiState.completionsByWeekday[it] ?: 0).toFloat() }
    val weakIndex = uiState.weakestWeekday?.let { weekdays.indexOf(it) }

    SectionCard(
        title = "Your weekly pattern",
        subtitle = "Completions on each weekday, all time",
    ) {
        MiniBars(
            values = counts,
            labels = weekdays.map {
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
            },
            highlightIndex = weakIndex,
        )
        uiState.weakestWeekday?.let { weakest ->
            Text(
                text = "${weakest.getDisplayName(TextStyle.FULL, Locale.getDefault())} is your " +
                    "weakest day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun VerdictCard(uiState: StatsUiState) {
    SectionCard(title = "Habit by habit", subtitle = "Sorted by how they are going") {
        VerdictGroup.entries.forEach { group ->
            val rows = uiState.habitVerdicts.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            Text(
                text = when (group) {
                    VerdictGroup.GOING_WELL -> "GOING WELL"
                    VerdictGroup.SLIPPING -> "SLIPPING"
                    VerdictGroup.NOT_ENOUGH_DATA -> "NOT ENOUGH DATA YET"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            rows.forEach { VerdictRow(it) }
        }
    }
}

@Composable
private fun VerdictRow(verdict: HabitVerdict) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(verdict.colorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
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
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
        verdict.delta?.let { delta ->
            Icon(
                imageVector = if (delta >= 0f) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (delta >= 0f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(14.dp).padding(start = 2.dp),
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
    SectionCard(title = title, subtitle = detail) {
        Text(
            text = "Unlocks on day $daysNeeded — $daysSoFar so far.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // A ghost of the real chart, so the shape of what is coming is visible.
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
    SectionCard(title = "No stats yet", subtitle = "Tick off a habit and this screen fills in") {
        Text(
            text = "Your completion rate, streak records and weekly pattern all appear here once " +
                "you have a few days of history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
