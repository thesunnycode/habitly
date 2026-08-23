package com.habitly.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private fun iconFor(destination: Destination) = when (destination) {
    Destination.Home -> Icons.Filled.Home
    Destination.Stats -> Icons.Filled.ShowChart
    Destination.Calendar -> Icons.Filled.CalendarMonth
    Destination.Achievements -> Icons.Filled.EmojiEvents
    Destination.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Home
}

private fun labelFor(destination: Destination) = when (destination) {
    Destination.Home -> "Home"
    Destination.Stats -> "Stats"
    Destination.Calendar -> "Calendar"
    Destination.Achievements -> "Awards"
    Destination.Settings -> "Settings"
    else -> ""
}

@Composable
fun HabitlyScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (BOTTOM_NAV_DESTINATIONS.any { it.route == currentRoute }) {
                NavigationBar {
                    BOTTOM_NAV_DESTINATIONS.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(iconFor(destination), contentDescription = labelFor(destination)) },
                            label = { Text(labelFor(destination)) },
                        )
                    }
                }
            }
        },
    ) { padding -> content(padding) }
}
