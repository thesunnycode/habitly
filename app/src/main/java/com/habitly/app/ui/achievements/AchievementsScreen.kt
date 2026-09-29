package com.habitly.app.ui.achievements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.domain.AchievementGroup
import com.habitly.app.domain.AchievementType
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.theme.HabitlyTheme
import java.time.format.DateTimeFormatter

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun AchievementsScreen(uiState: AchievementsUiState, onBack: () -> Unit) {
    var openType by rememberSaveable { mutableStateOf<AchievementType?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column {
                ScreenHeader(title = "Awards", onBack = onBack)
                BadgeProgressBar(
                    fraction = if (uiState.total == 0) 0f else uiState.unlockedCount.toFloat() / uiState.total,
                    height = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${uiState.unlockedCount} of ${uiState.total} unlocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        uiState.groups.forEach { (group, items) ->
            if (items.isEmpty()) return@forEach
            item(span = { GridItemSpan(maxLineSpan) }, key = "group-${group.name}") {
                SectionLabel(group.title, modifier = Modifier.padding(top = 12.dp))
            }
            items(items, key = { it.type.name }) { item -> BadgeTile(item, onClick = { openType = item.type }) }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }

    val open = openType?.let { type -> uiState.items.firstOrNull { it.type == type } }
    if (open != null) BadgeSheet(item = open, onDismiss = { openType = null })
}

@Composable
private fun BadgeTile(item: AchievementProgress, onClick: () -> Unit) {
    val unlockedAt = item.unlockedAt
    val status = if (unlockedAt != null) {
        "unlocked ${formatUnlockDate(unlockedAt, SHORT_DATE)}"
    } else {
        "locked, ${item.progressLabel()}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${item.type.title}, $status" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeCircle(item.type, unlocked = unlockedAt != null, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.type.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (unlockedAt != null) {
                Text(
                    text = formatUnlockDate(unlockedAt, SHORT_DATE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BadgeProgressBar(item.fraction(), height = 4.dp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.progressLabel(compact = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun previewState(): AchievementsUiState {
    val items = AchievementType.entries.mapIndexed { i, type ->
        AchievementProgress(type, unlockedAt = if (i % 3 == 0) 1_758_000_000_000L else null, progress = type.target / 2)
    }
    return AchievementsUiState(
        items = items,
        unlockedCount = items.count { it.unlockedAt != null },
        groups = AchievementGroup.entries.associateWith { g -> items.filter { it.type.group == g } },
    )
}

@Preview @Composable private fun AchievementsDarkPreview() = HabitlyTheme(darkTheme = true) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) { AchievementsScreen(previewState(), onBack = {}) }
}
@Preview @Composable private fun AchievementsLightPreview() = HabitlyTheme(darkTheme = false) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) { AchievementsScreen(previewState(), onBack = {}) }
}
