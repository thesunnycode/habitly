package com.habitly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.LocalDate

/** The Today-screen habit card: identity and check on top, a full-width contribution grid below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitGridCard(
    name: String,
    iconKey: String,
    colorHex: String,
    meta: String,
    checkState: CheckState,
    levels: Map<LocalDate, Int>,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A fixed week count stretched to the card width; null (the default) packs [cellSize] cells instead. */
    weeks: Int? = null,
    cellSize: Dp = 10.dp,
    endDate: LocalDate = LocalDate.now(),
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    streakDays: Int = 0,
) {
    val extras = LocalHabitlyColors.current
    val tones = habitTones(colorHex)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction("Mark done") { onCheck(); true })
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (extras.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(iconKey, colorHex, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streakDays > 0) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = tones.full,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CheckButton(
                    state = checkState,
                    colorHex = colorHex,
                    onClick = onCheck,
                    contentDescription = "Mark $name",
                )
            }
            Spacer(Modifier.height(10.dp))
            HabitGrid(
                levels = levels,
                colorHex = colorHex,
                weeks = weeks ?: 16,
                cellSize = if (weeks == null) cellSize else null,
                cellGap = 3.dp,
                endDate = endDate,
                weekStart = weekStart,
                summary = { shown ->
                    val from = GridMath.weekColumns(endDate, shown, weekStart).first()
                    val doneDays = levels.count { (date, level) -> level >= 4 && !date.isBefore(from) && !date.isAfter(endDate) }
                    "$name: $doneDays days done in the last $shown weeks"
                },
            )
        }
    }
}

@Composable
private fun HabitGridCardPreviewContent() {
    val end = LocalDate.of(2026, 9, 29)
    val levels = (0 until 112).associate { end.minusDays(it.toLong()) to (it * 3 % 5) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HabitGridCard(
                "Morning run", "run", "#FF922B", "12 day streak", CheckState.DONE, levels,
                {}, {}, {}, endDate = end, streakDays = 12,
            )
            HabitGridCard(
                "Read", "menu_book", "#9775FA", "0 of 20 pages", CheckState.EMPTY, levels,
                {}, {}, {}, endDate = end,
            )
        }
    }
}

@Preview @Composable private fun HabitGridCardDarkPreview() = HabitlyTheme(darkTheme = true) { HabitGridCardPreviewContent() }
@Preview @Composable private fun HabitGridCardLightPreview() = HabitlyTheme(darkTheme = false) { HabitGridCardPreviewContent() }
