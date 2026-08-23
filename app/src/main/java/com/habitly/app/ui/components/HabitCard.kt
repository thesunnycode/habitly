package com.habitly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import java.time.LocalDate

private fun parseHabitColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF6B9080))

/** White on a dark card, near-black on a light/pastel card — whichever the card color needs. */
private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1B1F1D) else Color.White

/**
 * Full-bleed solid-color card per habit ("Daily Habit Planner" style) with a seven-day history
 * strip underneath ("HabitKit" style) so every row shows momentum, not just today's state.
 *
 * Each state carries a distinct glyph as well as a distinct fill — a tick, a dash for a declared
 * rest day, a cross for a miss — because fill alone would leave the states indistinguishable to
 * anyone who cannot separate the colors.
 */
@Composable
fun HabitCard(
    habit: HabitWithStats,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSetStatus: ((CompletionStatus?) -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    val haptics = LocalHapticFeedback.current
    val done = habit.isCompletedToday
    var showStatusSheet by remember { mutableStateOf(false) }

    // A completed card recedes by blending toward the page, not by going translucent: alpha on the
    // whole card would also fade the text and day pills, which reads as muddy on a dark theme.
    val cardColor = lerp(
        parseHabitColor(habit.habit.colorHex),
        MaterialTheme.colorScheme.surface,
        if (done) 0.55f else 0f,
    )
    val contentColor = contentColorFor(cardColor)

    val checkFill by animateColorAsState(
        targetValue = if (done) contentColor else Color.Transparent,
        label = "checkFill",
    )
    val checkScale by animateFloatAsState(targetValue = if (done) 1f else 0.6f, label = "checkScale")

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = habitIconFor(habit.habit.icon),
                        contentDescription = null,
                        tint = contentColor,
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        text = habit.habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                    Text(
                        text = statusLineFor(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                }

                TodayControl(
                    habit = habit,
                    cardColor = cardColor,
                    contentColor = contentColor,
                    checkFill = checkFill,
                    checkScale = checkScale,
                    onToggle = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                    onLongPress = if (onSetStatus == null) {
                        null
                    } else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showStatusSheet = true
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            WeekHistoryStrip(habit = habit, contentColor = contentColor, cardColor = cardColor, today = today)
        }
    }

    if (showStatusSheet && onSetStatus != null) {
        HabitStatusSheet(
            habitName = habit.habit.name,
            current = habit.todayStatus,
            onSelect = { status ->
                showStatusSheet = false
                onSetStatus(status)
            },
            onDismiss = { showStatusSheet = false },
        )
    }
}

/**
 * The primary tap target for today. Drawn at 40dp but padded out to Android's 48dp minimum so the
 * touch area is never smaller than a fingertip.
 */
@Composable
private fun TodayControl(
    habit: HabitWithStats,
    cardColor: Color,
    contentColor: Color,
    checkFill: Color,
    checkScale: Float,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val status = habit.todayStatus
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .combinedClickable(
                role = Role.Checkbox,
                onClick = onToggle,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = habit.habit.name
                stateDescription = when (status) {
                    CompletionStatus.DONE -> "Done today"
                    CompletionStatus.SKIPPED -> "Rest day"
                    CompletionStatus.MISSED -> "Missed today"
                    null -> "Not recorded"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (status == CompletionStatus.DONE) checkFill else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = contentColor.copy(alpha = if (status == CompletionStatus.DONE) 0f else 0.6f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                CompletionStatus.DONE -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = cardColor,
                    modifier = Modifier.scale(checkScale),
                )
                CompletionStatus.SKIPPED -> Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    tint = contentColor,
                )
                CompletionStatus.MISSED -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = contentColor,
                )
                null -> Unit
            }
        }
    }
}

/**
 * Seven day pills, oldest first. The strip is decorative for a screen reader — seven focus stops per
 * card would bury the one control that matters — so it carries a single summary label instead.
 */
@Composable
private fun WeekHistoryStrip(
    habit: HabitWithStats,
    contentColor: Color,
    cardColor: Color,
    today: LocalDate,
) {
    val statuses = habit.last7DaysStatus.takeIf { it.size == 7 }
        ?: habit.last7Days.takeIf { it.size == 7 }?.map { done ->
            if (done) CompletionStatus.DONE else null
        }
        ?: List(7) { null }
    val due = habit.last7DaysDue.takeIf { it.size == 7 } ?: List(7) { true }
    val doneCount = statuses.count { it == CompletionStatus.DONE }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$doneCount of the last 7 days completed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        statuses.forEachIndexed { index, status ->
            val date = today.minusDays((6 - index).toLong())
            DayPill(
                label = date.dayOfWeek.name.take(1),
                status = status,
                isToday = index == 6,
                isDue = due[index],
                contentColor = contentColor,
                cardColor = cardColor,
            )
            if (index < 6) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun DayPill(
    label: String,
    status: CompletionStatus?,
    isToday: Boolean,
    isDue: Boolean,
    contentColor: Color,
    cardColor: Color,
) {
    val fill = when {
        status == CompletionStatus.DONE -> contentColor
        status == CompletionStatus.SKIPPED -> contentColor.copy(alpha = 0.35f)
        !isDue -> contentColor.copy(alpha = 0.08f)
        else -> contentColor.copy(alpha = 0.16f)
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (isToday && status != CompletionStatus.DONE) {
                    Modifier.border(1.5.dp, contentColor.copy(alpha = 0.7f), CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            // Glyphs, not just fills: the state has to survive being seen in greyscale.
            CompletionStatus.SKIPPED -> Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            CompletionStatus.MISSED -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.9f),
                modifier = Modifier.size(13.dp),
            )
            else -> Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (status == CompletionStatus.DONE) {
                    cardColor
                } else {
                    contentColor.copy(alpha = if (isDue) 0.85f else 0.45f)
                },
            )
        }
    }
}

private fun statusLineFor(habit: HabitWithStats): String {
    val parts = mutableListOf(habit.habit.category)
    habit.habit.reminderTime?.let { parts.add(it) }
    parts.add(
        when {
            habit.currentStreak > 0 -> "🔥 ${habit.currentStreak} day streak"
            habit.isCompletedToday -> "Streak started"
            !habit.isDueToday -> "Not due today"
            else -> "No streak yet"
        }
    )
    return parts.joinToString("  •  ")
}
