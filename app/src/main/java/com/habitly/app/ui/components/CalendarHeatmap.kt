package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.CompletionRecord
import java.time.YearMonth

@Composable
fun CalendarHeatmap(
    records: List<CompletionRecord>,
    month: YearMonth,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val completedDates = records.filter { it.completed }.map { it.date }.toSet()
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = firstDay.dayOfWeek.value % 7

    val cells = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(month.atDay(day))
    }

    val activeColor = color
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    val color = when {
                        date == null -> androidx.compose.ui.graphics.Color.Transparent
                        completedDates.contains(date) -> activeColor
                        else -> inactiveColor
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = color, shape = RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}
