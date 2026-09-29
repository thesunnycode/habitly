package com.habitly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Floating bottom navigation: a rounded surface with an icon pill behind the selected destination. */
@Composable
fun HabitlyNavBar(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalHabitlyColors.current
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (extras.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = if (extras.isDark) 0.dp else 4.dp,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                NavBarItem(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val extras = LocalHabitlyColors.current
    val spec = if (LocalReduceMotion.current) snap<androidx.compose.ui.graphics.Color>() else tween(200)
    val pill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = spec,
        label = "navPill",
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else extras.onSurfaceFaint,
        animationSpec = spec,
        label = "navTint",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(width = 64.dp, height = 32.dp).clip(CircleShape).background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else extras.onSurfaceFaint,
            maxLines = 1,
        )
    }
}

@Composable
private fun HabitlyNavBarPreviewContent() {
    val items = listOf(
        NavItem("today", "Today", Icons.Outlined.GridView, Icons.Rounded.GridView),
        NavItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    )
    Surface(color = MaterialTheme.colorScheme.background) {
        HabitlyNavBar(items, selectedRoute = "today", onSelect = {})
    }
}

@Preview @Composable private fun HabitlyNavBarDarkPreview() = HabitlyTheme(darkTheme = true) { HabitlyNavBarPreviewContent() }
@Preview @Composable private fun HabitlyNavBarLightPreview() = HabitlyTheme(darkTheme = false) { HabitlyNavBarPreviewContent() }
