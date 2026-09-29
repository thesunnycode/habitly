package com.habitly.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.ContributionLevels
import com.habitly.app.ui.components.CalendarHeatmap
import com.habitly.app.ui.components.ContributionGrid
import com.habitly.app.ui.components.ContributionLegend
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard
import com.habitly.app.ui.components.StatTile
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onMonthChange: (YearMonth) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val completedDays = uiState.mergedRecords
        .filter { it.completed && YearMonth.from(it.date) == uiState.month }
        .map { it.date }
        .distinct()
    val daysInMonth = uiState.month.lengthOfMonth()
    val daysElapsed = if (YearMonth.from(today) == uiState.month) today.dayOfMonth else daysInMonth

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Calendar",
                subtitle = "Every day you showed up",
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Days active",
                    value = "${completedDays.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Consistency so far",
                    value = "${if (daysElapsed == 0) 0 else completedDays.size * 100 / daysElapsed}%",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item {
            SectionCard(
                title = "Activity",
                subtitle = "Every day of the last six months",
            ) {
                ContributionGrid(
                    levels = ContributionLevels.forAllHabits(uiState.completionsByDate, uiState.habitCount),
                    endDate = today,
                )
                ContributionLegend(modifier = Modifier.padding(top = 12.dp))
            }
        }

        item {
            SectionCard(title = "Monthly view") {
                MonthSwitcher(month = uiState.month, onMonthChange = onMonthChange)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                CalendarHeatmap(
                    records = uiState.mergedRecords,
                    month = uiState.month,
                    modifier = Modifier.padding(top = 12.dp),
                )
                HeatmapLegend(modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.atDay(1).format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

/** A key for what the cells mean — the days are filled or not, so there is no gradient to explain. */
@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LegendKey(
            label = "Completed",
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        LegendKey(
            label = "Missed",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        )
    }
}

@Composable
private fun LegendKey(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
