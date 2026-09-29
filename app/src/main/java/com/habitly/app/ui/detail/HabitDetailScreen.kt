package com.habitly.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.label
import com.habitly.app.ui.components.ConfirmSheet
import com.habitly.app.ui.components.GridMath
import com.habitly.app.ui.components.HabitGrid
import com.habitly.app.ui.components.HabitStatusSheet
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.MonthGrid
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.theme.HabitTones
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import com.habitly.app.ui.theme.readableOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val REPAIR_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private val CARD_SHAPE = RoundedCornerShape(20.dp)

/**
 * One habit in full: its year of history, streak numbers, an editable month and a sticky
 * "Done today" pill. [onDayLongPress] receives the status (null = clear) and note picked in the
 * status sheet a long-press opens.
 */
@Composable
fun HabitDetailScreen(
    uiState: HabitDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onDayLongPress: (LocalDate, CompletionStatus?, String?) -> Unit,
    onToggleToday: () -> Unit,
    onRepairStreak: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val habit = uiState.habit
    val colorHex = habit?.colorHex ?: "#9775FA"
    val tones = habitTones(colorHex)
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var sheetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val statusByDate = remember(uiState.records) { uiState.records.associate { it.date to it.status } }
    val weeks = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 26 else 18
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailTopBar(
                    onBack = onBack,
                    onEdit = onEdit,
                    onArchive = onArchive,
                    onDelete = { showDeleteConfirm = true },
                )
            }
            if (habit != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(habit.icon, colorHex, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = habit.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(habit.category, uiState.schedule.label(), habit.reminderTime)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    DetailCard {
                        HabitGrid(
                            levels = uiState.levels,
                            colorHex = colorHex,
                            weeks = weeks,
                            endDate = uiState.today,
                            summary = { shown ->
                                val from = GridMath.weekColumns(uiState.today, shown, DayOfWeek.MONDAY).first()
                                val done = uiState.levels.count { (date, level) ->
                                    level >= 4 && !date.isBefore(from) && !date.isAfter(uiState.today)
                                }
                                "${habit.name}: $done days done in the last $shown weeks"
                            },
                        )
                        GridLegend(tones, Modifier.padding(top = 12.dp))
                    }
                }

                item { StatGrid(uiState, tones) }

                // A zero streak is never shown without a way back from it.
                val brokenDay = uiState.lastBrokenDueDay
                if (uiState.currentStreak == 0 && brokenDay != null && uiState.streakIfRepaired > 0) {
                    item {
                        StreakRepairCard(
                            brokenDay = brokenDay,
                            restoredStreak = uiState.streakIfRepaired,
                            tones = tones,
                            onRepair = onRepairStreak,
                        )
                    }
                }

                item {
                    MonthCard(
                        uiState = uiState,
                        tones = tones,
                        statusByDate = statusByDate,
                        onMonthChange = onMonthChange,
                        onToggleDay = onToggleDay,
                        onDayLongClick = { sheetDate = it },
                    )
                }
            }
        }

        if (habit != null) {
            val target = habit.targetCount
            val label = when {
                uiState.todayStatus == CompletionStatus.DONE -> "Undo today"
                target != null -> listOfNotNull("+1", habit.unit?.takeIf { it.isNotBlank() })
                    .joinToString(" ") + " (${uiState.todayProgress}/$target)"
                else -> "Done today"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                PrimaryButton(
                    text = label,
                    onClick = onToggleToday,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = tones.full,
                    contentColor = tones.onFull,
                )
            }
        }
    }

    val openDate = sheetDate
    if (habit != null && openDate != null) {
        val target = habit.targetCount
        // Goal habits show today's progress fraction in the sheet title.
        val title = if (target != null && openDate == uiState.today) {
            "${habit.name} · ${uiState.todayProgress}/$target"
        } else {
            habit.name
        }
        HabitStatusSheet(
            habitName = title,
            current = statusByDate[openDate],
            onSelect = { status, note ->
                sheetDate = null
                onDayLongPress(openDate, status, note)
            },
            onDismiss = { sheetDate = null },
        )
    }

    if (habit != null && showDeleteConfirm) {
        ConfirmSheet(
            title = "Delete habit?",
            message = "This removes ‘${habit.name}’ and all of its history. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = LocalHabitlyColors.current.surfaceRaised,
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                    onClick = { menuOpen = false; onArchive() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val extras = LocalHabitlyColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = if (extras.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun GridLegend(tones: HabitTones, modifier: Modifier = Modifier) {
    val extras = LocalHabitlyColors.current
    val steps = listOf(extras.gridEmpty, tones.low, tones.mid, lerp(tones.mid, tones.full, 0.5f), tones.full)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        steps.forEach { color ->
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatGrid(uiState: HabitDetailUiState, tones: HabitTones) {
    val accent = tones.full.readableOn(MaterialTheme.colorScheme.surface, 3f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = "${uiState.currentStreak}",
                label = "Current streak",
                modifier = Modifier.weight(1f),
                valueColor = accent,
                icon = Icons.Rounded.LocalFireDepartment,
            )
            StatTile(value = "${uiState.bestStreak}", label = "Best streak", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = "${(uiState.completionRate * 100).toInt()}%",
                label = "Rate",
                modifier = Modifier.weight(1f),
            )
            StatTile(value = "${uiState.totalDays}", label = "Total days", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakRepairCard(
    brokenDay: LocalDate,
    restoredStreak: Int,
    tones: HabitTones,
    onRepair: () -> Unit,
) {
    DetailCard {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(tones.full),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Streak ended on ${brokenDay.format(REPAIR_DAY_FORMAT)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Was that a day off rather than a miss? Mark it as a rest day and your " +
                        "$restoredStreak-day streak carries on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SecondaryButton(
                    text = "Make it a rest day",
                    onClick = onRepair,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthCard(
    uiState: HabitDetailUiState,
    tones: HabitTones,
    statusByDate: Map<LocalDate, CompletionStatus>,
    onMonthChange: (YearMonth) -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
) {
    val extras = LocalHabitlyColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    DetailCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(uiState.month.minusMonths(1)) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = uiState.month.atDay(1).format(MONTH_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
            )
            IconButton(onClick = { onMonthChange(uiState.month.plusMonths(1)) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
            }
        }
        MonthGrid(
            month = uiState.month,
            today = uiState.today,
            modifier = Modifier.padding(top = 8.dp),
            cellFill = { date ->
                when (statusByDate[date]) {
                    CompletionStatus.DONE -> tones.full
                    CompletionStatus.SKIPPED -> tones.low
                    CompletionStatus.PARTIAL -> tones.mid
                    else -> extras.gridEmpty
                }
            },
            cellText = { date -> if (statusByDate[date] == CompletionStatus.DONE) tones.onFull else onSurface },
            onDayClick = onToggleDay,
            onDayLongClick = onDayLongClick,
            dayDescription = { date ->
                val state = when (statusByDate[date]) {
                    CompletionStatus.DONE -> "done"
                    CompletionStatus.SKIPPED -> "rest day"
                    CompletionStatus.PARTIAL -> "partly done"
                    CompletionStatus.MISSED -> "missed"
                    null -> if (date.isAfter(uiState.today)) "upcoming" else "not logged"
                }
                "${date.format(DAY_FORMAT)}, $state"
            },
        )
    }
}
