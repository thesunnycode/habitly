package com.habitly.app.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.AchievementType
import com.habitly.app.ui.components.ScreenHeader

private fun labelFor(type: AchievementType) = when (type) {
    AchievementType.FIRST_HABIT -> "First Habit"
    AchievementType.STREAK_7 -> "7-Day Streak"
    AchievementType.STREAK_30 -> "30-Day Streak"
    AchievementType.STREAK_100 -> "100-Day Streak"
}

/** Saying what unlocks a badge turns a locked tile into a goal instead of a blank. */
private fun requirementFor(type: AchievementType) = when (type) {
    AchievementType.FIRST_HABIT -> "Create your first habit"
    AchievementType.STREAK_7 -> "Keep any habit for 7 days"
    AchievementType.STREAK_30 -> "Keep any habit for 30 days"
    AchievementType.STREAK_100 -> "Keep any habit for 100 days"
}

@Composable
fun AchievementsScreen(uiState: AchievementsUiState) {
    val unlocked = uiState.items.count { it.unlocked }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader(
                title = "Awards",
                subtitle = "$unlocked of ${uiState.items.size} unlocked",
            )
        }
        items(uiState.items) { item -> AchievementTile(item) }
    }
}

@Composable
private fun AchievementTile(item: AchievementUiItem) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.unlocked) {
                accent.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.unlocked) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = if (item.unlocked) "Unlocked" else "Locked",
                    tint = if (item.unlocked) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = labelFor(item.type),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (item.unlocked) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = if (item.unlocked) "Unlocked" else requirementFor(item.type),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
