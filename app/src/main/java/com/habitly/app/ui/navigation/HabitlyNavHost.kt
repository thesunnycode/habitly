package com.habitly.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.ui.achievements.AchievementsScreen
import com.habitly.app.ui.achievements.AchievementsViewModel
import com.habitly.app.ui.addedit.AddEditHabitScreen
import com.habitly.app.ui.addedit.AddEditHabitViewModel
import com.habitly.app.ui.allhabits.AllHabitsScreen
import com.habitly.app.ui.allhabits.AllHabitsViewModel
import com.habitly.app.ui.calendar.CalendarScreen
import com.habitly.app.ui.calendar.CalendarViewModel
import com.habitly.app.ui.components.NotificationPermissionPrompt
import com.habitly.app.ui.detail.HabitDetailScreen
import com.habitly.app.ui.detail.HabitDetailViewModel
import com.habitly.app.ui.home.HomeScreen
import com.habitly.app.ui.home.HomeViewModel
import com.habitly.app.ui.insights.InsightsScreen
import com.habitly.app.ui.onboarding.OnboardingScreen
import com.habitly.app.ui.reminders.RemindersScreen
import com.habitly.app.ui.settings.SettingsScreen
import com.habitly.app.ui.settings.SettingsViewModel
import com.habitly.app.ui.stats.StatsScreen
import com.habitly.app.ui.stats.StatsViewModel
import com.habitly.app.ui.you.YouScreen

@Composable
fun HabitlyNavHost(
    navController: NavHostController,
    startDestination: String,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val repository = rememberHabitRepository()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destination.Onboarding.route) {
            OnboardingScreen(onFinished = {
                navController.navigate(Destination.Today.route) { popUpTo(Destination.Onboarding.route) { inclusive = true } }
            })
        }
        composable(Destination.Today.route) {
            val viewModel = habitlyViewModel { HomeViewModel(repository) }
            val uiState by viewModel.uiState.collectAsState()
            NotificationPermissionPrompt(hasLoggedSomething = uiState.habits.any { it.isCompletedToday })
            HomeScreen(
                uiState = uiState,
                onToggleHabit = viewModel::onToggleHabit,
                onSetStatus = viewModel::onSetStatus,
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                onSeeAllClick = { navController.navigate(Destination.Habits.createRoute()) },
                onSelectDate = viewModel::onSelectDate,
                windowWidthSizeClass = windowWidthSizeClass,
            )
        }
        composable(
            route = Destination.Habits.route,
            arguments = listOf(navArgument("segment") { type = NavType.StringType; defaultValue = "active" }),
        ) {
            val viewModel = habitlyViewModel { AllHabitsViewModel(repository) }
            val uiState by viewModel.uiState.collectAsState()
            AllHabitsScreen(
                uiState = uiState,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onCategorySelect = viewModel::onCategorySelect,
                onToggleHabit = viewModel::onToggleHabit,
                onSetStatus = viewModel::onSetStatus,
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
            )
        }
        composable(
            route = Destination.Insights.route,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "stats" }),
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "stats"
            InsightsScreen(
                tab = tab,
                onTabChange = { next ->
                    navController.navigate(Destination.Insights.createRoute(next)) {
                        popUpTo(Destination.Today.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                statsContent = {
                    val viewModel = habitlyViewModel { StatsViewModel(repository) }
                    val uiState by viewModel.uiState.collectAsState()
                    StatsScreen(uiState = uiState, onPeriodChange = viewModel::onPeriodChange)
                },
                calendarContent = {
                    val viewModel = habitlyViewModel { CalendarViewModel(repository) }
                    val uiState by viewModel.uiState.collectAsState()
                    CalendarScreen(uiState = uiState, onMonthChange = viewModel::onMonthChange)
                },
            )
        }
        composable(Destination.You.route) {
            YouScreen(
                onAwards = { navController.navigate(Destination.Awards.route) },
                onArchived = { navController.navigate(Destination.Habits.createRoute("archived")) },
                onSettings = { navController.navigate(Destination.Settings.route) },
            )
        }
        composable(Destination.Awards.route) {
            val viewModel = habitlyViewModel { AchievementsViewModel(repository) }
            val uiState by viewModel.uiState.collectAsState()
            AchievementsScreen(uiState = uiState, onBack = { navController.popBackStack() })
        }
        composable(Destination.Settings.route) {
            val context = LocalContext.current
            val prefs = remember { SettingsPrefs(context) }
            val viewModel = habitlyViewModel { SettingsViewModel(prefs, repository) }
            val uiState by viewModel.uiState.collectAsState()
            SettingsScreen(
                uiState = uiState,
                onThemeModeChange = viewModel::onThemeModeChange,
                onNotificationsToggle = viewModel::onNotificationsToggle,
                onResetAllData = viewModel::onResetAllData,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.Reminders.route) {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Destination.AddEditHabit.route,
            arguments = listOf(
                navArgument("habitId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("template") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val habitId = (backStackEntry.arguments?.getLong("habitId") ?: -1L).takeIf { it != -1L }
            val viewModel = habitlyViewModel { AddEditHabitViewModel(repository, habitId) }
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isSaved) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
            AddEditHabitScreen(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onIconChange = viewModel::onIconChange,
                onColorChange = viewModel::onColorChange,
                onFrequencyTypeChange = viewModel::onFrequencyTypeChange,
                onSpecificDaysChange = viewModel::onSpecificDaysChange,
                onIntervalDaysChange = viewModel::onIntervalDaysChange,
                onSave = viewModel::onSave,
                onTemplateSelected = viewModel::onTemplateSelected,
                onTargetCountChange = viewModel::onTargetCountChange,
                onUnitChange = viewModel::onUnitChange,
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Destination.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getLong("habitId") ?: return@composable
            val viewModel = habitlyViewModel { HabitDetailViewModel(repository, habitId) }
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isDeleted) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
            HabitDetailScreen(
                uiState = uiState,
                onArchive = viewModel::onArchive,
                onDelete = viewModel::onDelete,
                onMonthChange = viewModel::onMonthChange,
                onRepairStreak = viewModel::onRepairStreak,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
