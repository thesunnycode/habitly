package com.habitly.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.ContributionLevels
import com.habitly.app.ui.components.CalendarHeatmap
import com.habitly.app.ui.components.ContributionGrid
import com.habitly.app.ui.components.ContributionLegend
import com.habitly.app.ui.components.ConfirmDialog
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.components.habitIconFor
import com.habitly.app.ui.theme.readableOn
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private val REPAIR_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

@Composable
private fun StreakRepairCard(
    brokenDay: java.time.LocalDate,
    restoredStreak: Int,
    accent: Color,
    onRepair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "Streak ended on ${brokenDay.format(REPAIR_DAY_FORMAT)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Was that a day off rather than a miss? Mark it as a rest day and your " +
                    "$restoredStreak-day streak carries on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onRepair, modifier = Modifier.padding(top = 6.dp)) {
                Text("Make it a rest day")
            }
        }
    }
}

@Composable
fun HabitDetailScreen(
    uiState: HabitDetailUiState,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onRepairStreak: () -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val habitColor = uiState.habit?.colorHex?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = uiState.habit?.name ?: "",
                subtitle = uiState.habit?.let { habit ->
                    listOfNotNull(habit.category, habit.reminderTime).joinToString("  •  ")
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archive habit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete habit",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(habitColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = habitIconFor(uiState.habit?.icon ?: "star"),
                        contentDescription = null,
                        tint = habitColor.readableOn(MaterialTheme.colorScheme.surface, target = 3f),
                    )
                }
                Text(
                    text = if (uiState.currentStreak > 0) {
                        "🔥 ${uiState.currentStreak}-day streak going"
                    } else {
                        "No active streak — today is a good day to start"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Current streak",
                    value = "${uiState.currentStreak}",
                    modifier = Modifier.weight(1f),
                    valueColor = habitColor,
                )
                StatTile(
                    label = "Best streak",
                    value = "${uiState.bestStreak}",
                    modifier = Modifier.weight(1f),
                    valueColor = habitColor,
                )
                StatTile(
                    label = "Completion",
                    value = "${(uiState.completionRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                    valueColor = habitColor,
                )
            }
        }

        // A zero streak is never shown without a way back from it.
        if (uiState.currentStreak == 0 && uiState.lastBrokenDueDay != null && uiState.streakIfRepaired > 0) {
            item {
                StreakRepairCard(
                    brokenDay = uiState.lastBrokenDueDay,
                    restoredStreak = uiState.streakIfRepaired,
                    accent = habitColor,
                    onRepair = onRepairStreak,
                )
            }
        }

        item {
            SectionCard(title = "Consistency", subtitle = "Every day since you started") {
                ContributionGrid(
                    levels = ContributionLevels.forHabit(uiState.records),
                    color = habitColor,
                )
                ContributionLegend(modifier = Modifier.padding(top = 12.dp), color = habitColor)
            }
        }

        item {
            SectionCard(title = "History") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onMonthChange(uiState.month.minusMonths(1)) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(
                        text = uiState.month.atDay(1).format(MONTH_FORMAT),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { onMonthChange(uiState.month.plusMonths(1)) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                    }
                }
                CalendarHeatmap(
                    records = uiState.records,
                    month = uiState.month,
                    color = habitColor,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete habit?",
            message = "This permanently deletes the habit and all its history.",
            confirmLabel = "Delete",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
