package com.habitly.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.domain.CompletionStatus
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
import com.habitly.app.ui.insights.InsightsScreen
import com.habitly.app.ui.onboarding.OnboardingScreen
import com.habitly.app.ui.reminders.RemindersScreen
import com.habitly.app.ui.settings.SettingsScreen
import com.habitly.app.ui.settings.SettingsViewModel
import com.habitly.app.ui.stats.StatsScreen
import com.habitly.app.ui.stats.StatsViewModel
import com.habitly.app.ui.today.TodayScreen
import com.habitly.app.ui.today.TodayViewModel
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
        composable(Destination.Today.route) { backStackEntry ->
            ArchivedUndoEffect(backStackEntry.savedStateHandle, repository)
            val context = LocalContext.current
            val prefs = remember { SettingsPrefs(context) }
            val viewModel = habitlyViewModel { TodayViewModel(repository, prefs) }
            val uiState by viewModel.uiState.collectAsState()
            NotificationPermissionPrompt(
                hasLoggedSomething = uiState.isViewingToday &&
                    uiState.done.any { it.todayStatus == CompletionStatus.DONE },
            )
            TodayScreen(
                uiState = uiState,
                onCheck = { habit ->
                    if (habit.habit.targetCount != null) {
                        viewModel.onIncrement(habit.habit.id)
                    } else {
                        viewModel.onToggleHabit(habit.habit.id)
                    }
                },
                onSetStatus = viewModel::onSetStatus,
                onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                onSelectDate = viewModel::onSelectDate,
                onAddHabit = { navController.navigate(Destination.AddEditHabit.createRoute()) },
                onTemplatePicked = { template ->
                    navController.navigate(Destination.AddEditHabit.createRoute(template = template?.key))
                },
                onCelebrationShown = viewModel::onCelebrationShown,
                windowWidthSizeClass = windowWidthSizeClass,
            )
        }
        composable(
            route = Destination.Habits.route,
            arguments = listOf(navArgument("segment") { type = NavType.StringType; defaultValue = "active" }),
        ) { backStackEntry ->
            ArchivedUndoEffect(backStackEntry.savedStateHandle, repository)
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
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "stats"
            var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
            InsightsScreen(
                tab = tab,
                onTabChange = { tab = it },
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
                onArchived = {
                    navController.navigate(Destination.Habits.createRoute("archived")) {
                        popUpTo(Destination.Today.route) { saveState = true }
                        launchSingleTop = true
                    }
                },
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
            val templateKey = backStackEntry.arguments?.getString("template")?.takeIf { it.isNotEmpty() }
            val viewModel = habitlyViewModel { AddEditHabitViewModel(repository, habitId, templateKey) }
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current
            val prefs = remember { SettingsPrefs(context) }
            val weekStart by prefs.weekStart.collectAsState(initial = java.time.DayOfWeek.MONDAY)
            if (uiState.isSaved || uiState.isNotFound) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
            AddEditHabitScreen(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onIconChange = viewModel::onIconChange,
                onColorChange = viewModel::onColorChange,
                onCategoryChange = viewModel::onCategoryChange,
                onFrequencyTypeChange = viewModel::onFrequencyTypeChange,
                onSpecificDaysChange = viewModel::onSpecificDaysChange,
                onIntervalDaysChange = viewModel::onIntervalDaysChange,
                onTargetCountChange = viewModel::onTargetCountChange,
                onUnitChange = viewModel::onUnitChange,
                onReminderTimeChange = viewModel::onReminderTimeChange,
                onSave = viewModel::onSave,
                onClose = { navController.popBackStack() },
                weekStart = weekStart,
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
            if (uiState.isArchived) {
                LaunchedEffect(Unit) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(ARCHIVED_HABIT_KEY, habitId)
                    navController.popBackStack()
                }
            }
            HabitDetailScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Destination.AddEditHabit.createRoute(habitId)) },
                onArchive = viewModel::onArchive,
                onDelete = viewModel::onDelete,
                onMonthChange = viewModel::onMonthChange,
                onToggleDay = viewModel::onToggleDay,
                onDayLongPress = viewModel::onSetDayStatus,
                onToggleToday = viewModel::onToggleToday,
                onRepairStreak = viewModel::onRepairStreak,
                windowWidthSizeClass = windowWidthSizeClass,
            )
        }
    }
}
