package com.habitly.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.habitly.app.ui.components.HabitlyNavBar
import com.habitly.app.ui.components.NavItem

private val NAV_ITEMS = listOf(
    NavItem(Destination.Today.route, "Today", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    NavItem(Destination.Habits.route, "Habits", Icons.Outlined.ViewAgenda, Icons.Rounded.ViewAgenda),
    NavItem(Destination.Insights.route, "Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
    NavItem(Destination.You.route, "You", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
)

private fun concreteRoute(item: NavItem): String = when (item.route) {
    Destination.Habits.route -> Destination.Habits.createRoute()
    Destination.Insights.route -> Destination.Insights.createRoute()
    else -> item.route
}

@Composable
fun HabitlyScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Content padding carries only the bottom bar's height; each screen owns its status-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTabRoute(currentRoute)) {
                HabitlyNavBar(
                    items = NAV_ITEMS,
                    selectedRoute = currentRoute,
                    onSelect = { item ->
                        navController.navigate(concreteRoute(item)) {
                            popUpTo(Destination.Today.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding -> content(padding) }
}
