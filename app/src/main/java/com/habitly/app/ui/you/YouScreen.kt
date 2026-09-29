package com.habitly.app.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementType
import com.habitly.app.ui.achievements.BadgeCircle
import com.habitly.app.ui.addedit.formFieldColors
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.ProgressRing
import com.habitly.app.ui.components.SettingsGroup
import com.habitly.app.ui.components.SettingsRow
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

@Composable
fun YouScreen(
    uiState: YouUiState,
    onNameChange: (String) -> Unit,
    onAwards: () -> Unit,
    onReminders: () -> Unit,
    onArchived: () -> Unit,
    onSettings: () -> Unit,
) {
    var editingName by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(uiState, onClick = { editingName = true })
        LifetimeTiles(uiState)
        AwardsPreview(uiState, onClick = onAwards)
        SettingsGroup {
            SettingsRow(title = "Reminders", icon = Icons.Rounded.NotificationsNone, onClick = onReminders)
            SettingsRow(title = "Archived habits", icon = Icons.Rounded.Inventory2, onClick = onArchived, showDivider = true)
            SettingsRow(title = "Settings", icon = Icons.Rounded.Tune, onClick = onSettings, showDivider = true)
        }
    }

    if (editingName) {
        NameSheet(
            current = uiState.name.orEmpty(),
            onSave = { onNameChange(it); editingName = false },
            onDismiss = { editingName = false },
        )
    }
}

private fun memberLine(state: YouUiState): String {
    val habits = if (state.activeHabits == 1) "1 habit" else "${state.activeHabits} habits"
    return state.memberSince?.let { "Since ${it.format(MONTH_YEAR)} · $habits" } ?: habits
}

@Composable
private fun ProfileHeader(state: YouUiState, onClick: () -> Unit) {
    val name = state.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Edit name", onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (name != null) {
                Text(
                    text = name.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: "Add your name",
                style = MaterialTheme.typography.titleLarge,
                color = if (name != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = memberLine(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LifetimeTiles(state: YouUiState) {
    val tile = Modifier.fillMaxHeight()
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            value = "${state.bestStreak}",
            label = "Best streak",
            modifier = Modifier.weight(1f).then(tile),
            icon = Icons.Rounded.LocalFireDepartment,
            iconTint = LocalHabitlyColors.current.warning,
        )
        StatTile(
            value = "${state.checkIns}",
            label = "Check-ins",
            modifier = Modifier.weight(1f).then(tile),
            icon = Icons.Rounded.TaskAlt,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatTile(
            value = "${state.perfectDays}",
            label = "Perfect days",
            modifier = Modifier.weight(1f).then(tile),
            icon = Icons.Rounded.Star,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AwardsPreview(state: YouUiState, onClick: () -> Unit) {
    val total = AchievementType.entries.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = "See all awards", onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Awards", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.unlockedCount} of $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        val next = state.nextBadge
        if (state.recentBadges.isNotEmpty() || next != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                state.recentBadges.forEach { badge ->
                    Box(
                        modifier = Modifier.size(56.dp).semantics { contentDescription = "${badge.type.title}, unlocked" },
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgeCircle(badge.type, unlocked = true, size = 48.dp)
                    }
                }
                if (next != null) NextBadge(next)
            }
        }
    }
}

/** The locked badge nearest to unlocking, ringed by its progress. */
@Composable
private fun NextBadge(badge: AchievementProgress) {
    Box(contentAlignment = Alignment.Center) {
        ProgressRing(
            progress = badge.progress.toFloat() / badge.type.target,
            label = "",
            description = "Next: ${badge.type.title}, ${badge.progress} of ${badge.type.target}",
            size = 56.dp,
            strokeWidth = 3.dp,
        )
        BadgeCircle(badge.type, unlocked = false, size = 44.dp)
    }
}

@Composable
private fun NameSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    HabitlySheet(onDismiss = onDismiss, title = "Your name") {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = formFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Save", onClick = { onSave(text) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun previewState() = YouUiState(
    name = "Sunny",
    memberSince = YearMonth.of(2026, 8),
    activeHabits = 4,
    bestStreak = 12,
    checkIns = 86,
    perfectDays = 9,
    recentBadges = listOf(
        AchievementProgress(AchievementType.STREAK_7, 3L, 7),
        AchievementProgress(AchievementType.CHECKINS_10, 2L, 10),
        AchievementProgress(AchievementType.FIRST_HABIT, 1L, 1),
    ),
    nextBadge = AchievementProgress(AchievementType.STREAK_30, null, 12),
    unlockedCount = 5,
)

@Composable
private fun YouPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        YouScreen(previewState(), {}, {}, {}, {}, {})
    }
}

@Preview @Composable private fun YouDarkPreview() = HabitlyTheme(darkTheme = true) { YouPreviewContent() }
@Preview @Composable private fun YouLightPreview() = HabitlyTheme(darkTheme = false) { YouPreviewContent() }
