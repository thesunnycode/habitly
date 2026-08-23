package com.habitly.app.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun HabitlyNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destination.Splash.route) { Text("TODO Splash") }
        composable(Destination.Onboarding.route) { Text("TODO Onboarding") }
        composable(Destination.Home.route) { Text("TODO Home") }
        composable(Destination.Stats.route) { Text("TODO Stats") }
        composable(Destination.Calendar.route) { Text("TODO Calendar") }
        composable(Destination.Achievements.route) { Text("TODO Achievements") }
        composable(Destination.Settings.route) { Text("TODO Settings") }
        composable(
            route = Destination.AddEditHabit.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L }),
        ) { Text("TODO AddEditHabit") }
        composable(
            route = Destination.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) { Text("TODO HabitDetail") }
    }
}
