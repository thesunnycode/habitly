package com.habitly.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.data.repository.HabitWithStats
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
 * Completed cards dim and strike through, and the check button animates + fires a haptic tick.
 */
@Composable
fun HabitCard(
    habit: HabitWithStats,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val haptics = LocalHapticFeedback.current
    val done = habit.isCompletedToday
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
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(checkFill)
                        .border(2.dp, contentColor.copy(alpha = if (done) 0f else 0.6f), CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        }
                        .semantics {
                            contentDescription = if (done) {
                                "Mark ${habit.habit.name} as not done today"
                            } else {
                                "Mark ${habit.habit.name} as done today"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.scale(checkScale),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            WeekHistoryStrip(
                last7Days = habit.habit7Days(),
                contentColor = contentColor,
                cardColor = cardColor,
                today = today,
            )
        }
    }
}

private fun HabitWithStats.habit7Days(): List<Boolean> =
    if (last7Days.size == 7) last7Days else List(7) { false }

/** Seven day-initial pills, oldest first, filled for the days this habit was completed. */
@Composable
private fun WeekHistoryStrip(
    last7Days: List<Boolean>,
    contentColor: Color,
    cardColor: Color,
    today: LocalDate,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        last7Days.forEachIndexed { index, completed ->
            val date = today.minusDays((6 - index).toLong())
            val isToday = index == 6
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (completed) contentColor else contentColor.copy(alpha = 0.16f),
                    )
                    .then(
                        if (isToday && !completed) {
                            Modifier.border(1.5.dp, contentColor.copy(alpha = 0.7f), CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfWeek.name.take(1),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (completed) cardColor else contentColor.copy(alpha = 0.85f),
                )
            }
            if (index < 6) Spacer(modifier = Modifier.width(6.dp))
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
            else -> "No streak yet"
        }
    )
    return parts.joinToString("  •  ")
}
