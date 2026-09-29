package com.habitly.app.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementGroup
import com.habitly.app.domain.AchievementType
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Each group wears one palette seed, so a row of badges reads as a set. */
internal fun groupSeed(group: AchievementGroup): String = when (group) {
    AchievementGroup.STREAKS -> "#FF922B"
    AchievementGroup.CONSISTENCY -> "#FCC419"
    AchievementGroup.VOLUME -> "#9775FA"
    AchievementGroup.SPECIAL -> "#22B8CF"
}

internal fun badgeGlyph(type: AchievementType): ImageVector = when (type) {
    AchievementType.STREAK_3, AchievementType.STREAK_7,
    AchievementType.STREAK_30, AchievementType.STREAK_100 -> Icons.Rounded.LocalFireDepartment
    AchievementType.PERFECT_DAY -> Icons.Rounded.Star
    AchievementType.PERFECT_WEEK -> Icons.Rounded.AutoAwesome
    AchievementType.MONTH_80 -> Icons.Rounded.CalendarMonth
    AchievementType.COMEBACK -> Icons.Rounded.Replay
    AchievementType.CHECKINS_10, AchievementType.CHECKINS_100, AchievementType.CHECKINS_500 -> Icons.Rounded.TaskAlt
    AchievementType.GOAL_10 -> Icons.Rounded.Flag
    AchievementType.FIRST_HABIT -> Icons.Rounded.Spa
    AchievementType.FIVE_HABITS -> Icons.Rounded.GridView
    AchievementType.EARLY_BIRD -> Icons.Rounded.WbTwilight
    AchievementType.SECOND_CHANCE -> Icons.Rounded.Bedtime
}

/** The round badge: group colours and glyph once earned, a grey lock until then. */
@Composable
internal fun BadgeCircle(type: AchievementType, unlocked: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val tones = habitTones(groupSeed(type.group))
    val colors = LocalHabitlyColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (unlocked) tones.low else colors.gridEmpty),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (unlocked) badgeGlyph(type) else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (unlocked) tones.full else colors.onSurfaceFaint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** A rounded progress bar on the empty-grid track. */
@Composable
internal fun BadgeProgressBar(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.height(height).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = LocalHabitlyColors.current.gridEmpty,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

internal fun AchievementProgress.fraction(): Float = progress.toFloat() / type.target

/**
 * "12 of 30", or "12/30" when [compact]. The month badge counts percent, so it reads "62% of 80%"
 * (compact "62% / 80%") rather than a bare "62/80".
 */
internal fun AchievementProgress.progressLabel(compact: Boolean = false): String = when {
    type == AchievementType.MONTH_80 -> if (compact) "$progress% / ${type.target}%" else "$progress% of ${type.target}%"
    compact -> "$progress/${type.target}"
    else -> "$progress of ${type.target}"
}

private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")

internal fun formatUnlockDate(millis: Long, formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)

/** Detail for one badge: what earns it, and either when it was earned or how far along it is. */
@Composable
fun BadgeSheet(item: AchievementProgress, onDismiss: () -> Unit) {
    HabitlySheet(onDismiss = onDismiss, skipPartiallyExpanded = true) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeCircle(item.type, unlocked = item.unlockedAt != null, size = 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = item.type.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.type.criterion,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            val unlockedAt = item.unlockedAt
            if (unlockedAt != null) {
                Text(
                    text = "Unlocked on ${formatUnlockDate(unlockedAt, LONG_DATE)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BadgeProgressBar(item.fraction(), height = 6.dp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.progressLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
