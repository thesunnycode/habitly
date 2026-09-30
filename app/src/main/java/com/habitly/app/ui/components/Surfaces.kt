package com.habitly.app.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import com.habitly.app.ui.theme.HabitlyTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalReduceMotion

/** One number with a caption on a surface card. Integer values can count up from [animateFrom]. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    iconTint: Color = valueColor,
    animateFrom: Int? = null,
) {
    val target = value.toIntOrNull()
    val animate = animateFrom != null && target != null && !LocalReduceMotion.current
    var goal by remember { mutableIntStateOf(if (animate) animateFrom!! else target ?: 0) }
    LaunchedEffect(target, animate) { if (target != null) goal = target }
    val counted by animateIntAsState(goal, animationSpec = tween(700), label = "statCount")
    val shown = if (animate) counted.toString() else value
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(text = shown, style = MaterialTheme.typography.headlineSmall, color = valueColor)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTilePreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("12", "Current streak", Modifier.weight(1f), icon = Icons.Rounded.LocalFireDepartment)
            StatTile("87%", "Completion", Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview @Composable private fun StatTileDarkPreview() = HabitlyTheme(darkTheme = true) { StatTilePreviewContent() }
@Preview @Composable private fun StatTileLightPreview() = HabitlyTheme(darkTheme = false) { StatTilePreviewContent() }
