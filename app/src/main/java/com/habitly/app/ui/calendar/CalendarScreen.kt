package com.habitly.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.MonthGrid
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.readableOn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

/** The four brand steps plus empty, from least to most of the day's due habits kept. */
@Composable
private fun brandSteps(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        LocalHabitlyColors.current.gridEmpty,
        scheme.primaryContainer,
        lerp(scheme.primaryContainer, scheme.primary, 0.5f),
        lerp(scheme.primaryContainer, scheme.primary, 0.75f),
        scheme.primary,
    )
}

private fun stepFor(fraction: Float?): Int = when {
    fraction == null || fraction <= 0f -> 0
    fraction < 0.34f -> 1
    fraction < 0.67f -> 2
    fraction < 1f -> 3
    else -> 4
}

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onDayDismissed: () -> Unit,
    onToggleHabitOnDay: (habitId: Long, date: LocalDate) -> Unit,
) {
    val steps = brandSteps()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val counts = uiState.dayCounts
    val daysActive = counts.values.count { it.first > 0 }
    val due = counts.values.sumOf { it.second }
    val kept = counts.values.sumOf { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Clear of the floating nav bar.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CalendarCard {
                MonthSwitcher(month = uiState.month, onMonthChange = onMonthChange)
                MonthGrid(
                    month = uiState.month,
                    today = uiState.today,
                    selected = uiState.selectedDate,
                    weekStart = uiState.weekStart,
                    modifier = Modifier.padding(top = 8.dp),
                    cellFill = { date -> steps[stepFor(uiState.dayFraction[date])] },
                    cellText = { date -> onSurface.readableOn(steps[stepFor(uiState.dayFraction[date])]) },
                    onDayClick = onDaySelected,
                    dayDescription = { date ->
                        val label = date.format(DAY_FORMAT)
                        val count = counts[date]
                        when {
                            date.isAfter(uiState.today) -> "$label, upcoming"
                            count == null -> "$label, nothing due"
                            else -> "$label, ${count.first} of ${count.second} done"
                        }
                    },
                )
                Legend(steps, modifier = Modifier.padding(top = 14.dp))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = "$daysActive",
                    label = "Days active",
                    animateFrom = 0,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${if (due == 0) 0 else kept * 100 / due}%",
                    label = "Consistency",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val selected = uiState.selectedDate
    if (selected != null) {
        DaySheet(
            date = selected,
            dayHabits = uiState.dayHabits,
            onToggle = { habitId -> onToggleHabitOnDay(habitId, selected) },
            onDismiss = onDayDismissed,
        )
    }
}

@Composable
private fun CalendarCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHabitlyColors.current.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.atDay(1).format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
        }
    }
}

/** Less → More key for the five fill steps. */
@Composable
private fun Legend(steps: List<Color>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Shading from less to more of the day's habits done" },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(end = 4.dp))
        steps.forEach { color ->
            Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(start = 4.dp))
    }
}
