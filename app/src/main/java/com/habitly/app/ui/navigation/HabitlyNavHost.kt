package com.habitly.app.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun HabitlyNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destination.Splash.route) {
            com.habitly.app.ui.splash.SplashScreen(onNavigate = { destination ->
                navController.navigate(destination) { popUpTo(Destination.Splash.route) { inclusive = true } }
            })
        }
        composable(Destination.Onboarding.route) {
            com.habitly.app.ui.onboarding.OnboardingScreen(onFinished = {
                navController.navigate(Destination.Home.route) { popUpTo(Destination.Onboarding.route) { inclusive = true } }
            })
        }
        composable(Destination.Home.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.home.HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.home.HomeViewModel(repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.home.HomeScreen(
                uiState = uiState,
                onToggleHabit = viewModel::onToggleHabit,
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
            )
        }
        composable(Destination.Stats.route) { Text("TODO Stats") }
        composable(Destination.Calendar.route) { Text("TODO Calendar") }
        composable(Destination.Achievements.route) { Text("TODO Achievements") }
        composable(Destination.Settings.route) { Text("TODO Settings") }
        composable(
            route = Destination.AddEditHabit.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong("habitId") ?: -1L
            val habitId = rawId.takeIf { it != -1L }
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.addedit.AddEditHabitViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.addedit.AddEditHabitViewModel(repository, habitId) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isSaved) {
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            }
            com.habitly.app.ui.addedit.AddEditHabitScreen(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onFrequencyTypeChange = viewModel::onFrequencyTypeChange,
                onSpecificDaysChange = viewModel::onSpecificDaysChange,
                onIntervalDaysChange = viewModel::onIntervalDaysChange,
                onSave = viewModel::onSave,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Destination.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getLong("habitId") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.detail.HabitDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.detail.HabitDetailViewModel(repository, habitId) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isDeleted) {
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            }
            com.habitly.app.ui.detail.HabitDetailScreen(
                uiState = uiState,
                onArchive = viewModel::onArchive,
                onDelete = viewModel::onDelete,
                onMonthChange = viewModel::onMonthChange,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
