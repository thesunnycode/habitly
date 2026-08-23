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
fun HabitlyNavHost(
    navController: NavHostController,
    startDestination: String,
    windowWidthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass =
        androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact,
) {
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
                onSetStatus = viewModel::onSetStatus,
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                onSeeAllClick = { navController.navigate(Destination.AllHabits.route) },
                windowWidthSizeClass = windowWidthSizeClass,
            )
        }
        composable(Destination.AllHabits.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.allhabits.AllHabitsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.allhabits.AllHabitsViewModel(repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.allhabits.AllHabitsScreen(
                uiState = uiState,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onCategorySelect = viewModel::onCategorySelect,
                onToggleHabit = viewModel::onToggleHabit,
                onSetStatus = viewModel::onSetStatus,
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
            )
        }
        composable(Destination.Stats.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.stats.StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.stats.StatsViewModel(repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.stats.StatsScreen(uiState = uiState)
        }
        composable(Destination.Calendar.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.calendar.CalendarViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.calendar.CalendarViewModel(repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.calendar.CalendarScreen(uiState = uiState, onMonthChange = viewModel::onMonthChange)
        }
        composable(Destination.Achievements.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val viewModel: com.habitly.app.ui.achievements.AchievementsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.achievements.AchievementsViewModel(repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.achievements.AchievementsScreen(uiState = uiState)
        }
        composable(Destination.Settings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember {
                val db = com.habitly.app.data.local.AppDatabase.getInstance(context)
                com.habitly.app.data.repository.HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
            }
            val prefs = remember { com.habitly.app.data.prefs.SettingsPrefs(context) }
            val viewModel: com.habitly.app.ui.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.habitly.app.ui.settings.SettingsViewModel(prefs, repository) as T
                    }
                },
            )
            val uiState by viewModel.uiState.collectAsState()
            com.habitly.app.ui.settings.SettingsScreen(
                uiState = uiState,
                onThemeModeChange = viewModel::onThemeModeChange,
                onNotificationsToggle = viewModel::onNotificationsToggle,
                onResetAllData = viewModel::onResetAllData,
            )
        }
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
                onIconChange = viewModel::onIconChange,
                onColorChange = viewModel::onColorChange,
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
