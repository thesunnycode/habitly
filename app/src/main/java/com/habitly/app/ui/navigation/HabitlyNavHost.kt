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
import com.habitly.app.ai.AiServices
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.prefs.OnboardingPrefs
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.seed.SeedDataProvider
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.ml.ForecastServices
import com.habitly.app.reminder.NotificationHelper
import com.habitly.app.ui.achievements.AchievementsScreen
import com.habitly.app.ui.achievements.AchievementsViewModel
import com.habitly.app.ui.achievements.LocalBadgeCelebrationShowing
import com.habitly.app.ui.addedit.AddEditHabitScreen
import com.habitly.app.ui.addedit.AddEditHabitViewModel
import com.habitly.app.ui.calendar.CalendarScreen
import com.habitly.app.ui.calendar.CalendarViewModel
import com.habitly.app.ui.components.CenteredContent
import com.habitly.app.ui.components.NotificationPermissionPrompt
import com.habitly.app.ui.detail.HabitDetailScreen
import com.habitly.app.ui.detail.HabitDetailViewModel
import com.habitly.app.ui.habits.HabitsScreen
import com.habitly.app.ui.habits.HabitsSegment
import com.habitly.app.ui.habits.HabitsViewModel
import com.habitly.app.ui.insights.InsightsScreen
import com.habitly.app.ui.onboarding.OnboardingScreen
import com.habitly.app.ui.onboarding.OnboardingStore
import com.habitly.app.ui.onboarding.OnboardingViewModel
import com.habitly.app.ui.reminders.RemindersScreen
import com.habitly.app.ui.reminders.RemindersViewModel
import com.habitly.app.ui.settings.SettingsScreen
import com.habitly.app.ui.settings.SettingsViewModel
import com.habitly.app.ui.stats.StatsPeriodSwitch
import com.habitly.app.ui.stats.StatsScreen
import com.habitly.app.ui.stats.StatsViewModel
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.today.TodayScreen
import com.habitly.app.ui.today.TodayViewModel
import com.habitly.app.ui.you.YouScreen
import com.habitly.app.ui.you.YouViewModel
import java.time.LocalDate

@Composable
fun HabitlyNavHost(
    navController: NavHostController,
    startDestination: String,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val repository = rememberHabitRepository()
    // Application context: the prefs outlive any one screen and must not hold the activity.
    val appContext = LocalContext.current.applicationContext
    val prefs = remember { SettingsPrefs(appContext) }
    val notifications = remember { NotificationHelper(appContext) }
    // Today and Insights have their own side-by-side layout at expanded widths; don't squeeze it.
    val twoPane = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    val transitions = NavTransitions(reduceMotion = LocalReduceMotion.current)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(Destination.Onboarding.route) {
            val viewModel = habitlyViewModel {
                val onboardingPrefs = OnboardingPrefs(appContext)
                OnboardingViewModel(
                    repository,
                    object : OnboardingStore {
                        override suspend fun setUserName(name: String?) = prefs.setUserName(name)
                        override suspend fun setCompleted() = onboardingPrefs.setCompleted()
                    },
                )
            }
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isFinished) {
                LaunchedEffect(Unit) {
                    navController.navigate(Destination.Today.route) { popUpTo(Destination.Onboarding.route) { inclusive = true } }
                }
            }
            OnboardingScreen(
                uiState = uiState,
                onNext = viewModel::onNext,
                onBack = viewModel::onBack,
                onNameChange = viewModel::onNameChange,
                onToggleTemplate = viewModel::onToggleTemplate,
                onSkip = viewModel::onSkip,
                onFinish = viewModel::onFinish,
            )
        }
        composable(Destination.Today.route) { backStackEntry ->
            ArchivedUndoEffect(backStackEntry.savedStateHandle, repository)
            val forecastRepo = remember { ForecastServices.repository(appContext) }
            // Retrains off the main thread only when the stored model is over a day old; check-ins just re-predict.
            LaunchedEffect(Unit) { forecastRepo.refreshIfStale() }
            val viewModel = habitlyViewModel {
                TodayViewModel(
                    repository, prefs, notifications = notifications,
                    forecast = forecastRepo.observeToday(),
                    showForecast = AiServices.prefs(appContext).showForecast,
                )
            }
            val uiState by viewModel.uiState.collectAsState()
            NotificationPermissionPrompt(
                hasLoggedSomething = uiState.isViewingToday &&
                    uiState.done.any { it.todayStatus == CompletionStatus.DONE },
                onTurnOnReminders = { navController.navigate(Destination.Reminders.route) },
                blocked = uiState.showCelebration || LocalBadgeCelebrationShowing.current,
            )
            CenteredContent(enabled = !twoPane) {
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
        }
        composable(
            route = Destination.Habits.route,
            arguments = listOf(navArgument("segment") { type = NavType.StringType; defaultValue = "active" }),
        ) { backStackEntry ->
            ArchivedUndoEffect(backStackEntry.savedStateHandle, repository)
            val segment = backStackEntry.arguments?.getString("segment") ?: "active"
            val initialSegment = if (segment == "archived") HabitsSegment.ARCHIVED else HabitsSegment.ACTIVE
            val viewModel = habitlyViewModel(key = "habits-$segment") { HabitsViewModel(repository, initialSegment, notifications = notifications) }
            val uiState by viewModel.uiState.collectAsState()
            CenteredContent {
                HabitsScreen(
                    uiState = uiState,
                    onSegmentChange = viewModel::onSegmentChange,
                    onQueryChange = viewModel::onQueryChange,
                    onCategorySelect = viewModel::onCategorySelect,
                    onSortChange = viewModel::onSortChange,
                    onClearFilters = viewModel::onClearFilters,
                    onArchive = viewModel::onArchive,
                    onRestore = viewModel::onRestore,
                    onDeleteForever = viewModel::onDeleteForever,
                    onMove = viewModel::onMove,
                    onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                    onAddHabit = { template ->
                        navController.navigate(Destination.AddEditHabit.createRoute(template = template?.key))
                    },
                )
            }
        }
        composable(
            route = Destination.Insights.route,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "stats" }),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "stats"
            var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
            // Hoisted out of the Stats slot: its W · M · Y switch sits in the Insights title row.
            val statsViewModel = habitlyViewModel { StatsViewModel(repository) }
            val statsState by statsViewModel.uiState.collectAsState()
            val weekStart by prefs.weekStart.collectAsState(initial = java.time.DayOfWeek.MONDAY)
            CenteredContent(enabled = !twoPane) {
                InsightsScreen(
                    tab = tab,
                    onTabChange = { tab = it },
                    windowWidthSizeClass = windowWidthSizeClass,
                    statsTitleTrailing = if (statsState.hasAnyHistory) {
                        { StatsPeriodSwitch(selected = statsState.period, onSelect = statsViewModel::onPeriodChange) }
                    } else {
                        null
                    },
                    stats = {
                        StatsScreen(
                            uiState = statsState,
                            onHabitFilterChange = statsViewModel::onHabitFilterChange,
                            onHabitClick = { id -> navController.navigate(Destination.HabitDetail.createRoute(id)) },
                            weekStart = weekStart,
                        )
                    },
                    calendar = {
                        val viewModel = habitlyViewModel { CalendarViewModel(repository, weekStart = prefs.weekStart, notifications = notifications) }
                        val uiState by viewModel.uiState.collectAsState()
                        CalendarScreen(
                            uiState = uiState,
                            onMonthChange = viewModel::onMonthChange,
                            onDaySelected = viewModel::onDaySelected,
                            onDayDismissed = viewModel::onDayDismissed,
                            onToggleHabitOnDay = viewModel::onToggleHabitOnDay,
                        )
                    },
                )
            }
        }
        composable(Destination.You.route) {
            val viewModel = habitlyViewModel { YouViewModel(repository, prefs) }
            val uiState by viewModel.uiState.collectAsState()
            CenteredContent {
                YouScreen(
                    uiState = uiState,
                    onNameChange = viewModel::onNameChange,
                    onAwards = { navController.navigate(Destination.Awards.route) },
                    onReminders = { navController.navigate(Destination.Reminders.route) },
                    onArchived = {
                        navController.navigate(Destination.Habits.createRoute("archived")) {
                            popUpTo(Destination.Today.route) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onSettings = { navController.navigate(Destination.Settings.route) },
                )
            }
        }
        composable(Destination.Awards.route) {
            val viewModel = habitlyViewModel { AchievementsViewModel(repository) }
            val uiState by viewModel.uiState.collectAsState()
            CenteredContent {
                AchievementsScreen(uiState = uiState, onBack = { navController.popBackStack() })
            }
        }
        composable(Destination.Settings.route) {
            val viewModel = habitlyViewModel {
                SettingsViewModel(
                    prefs = prefs,
                    repository = repository,
                    aiPrefs = AiServices.prefs(appContext),
                    notifications = notifications,
                    loadDemoData = {
                        // Silences the badges the demo history unlocks, so they don't all celebrate at once.
                        SeedDataProvider.seedAndSilenceBadges(
                            repository, AppDatabase.getInstance(appContext).habitDao(), LocalDate.now(), prefs::markBadgesSeen,
                        )
                    },
                )
            }
            val uiState by viewModel.uiState.collectAsState()
            CenteredContent {
                SettingsScreen(
                    uiState = uiState,
                    onThemeModeChange = viewModel::onThemeModeChange,
                    onWeekStartChange = viewModel::onWeekStartChange,
                    onNotificationsToggle = viewModel::onNotificationsToggle,
                    onStreakNudgeToggle = viewModel::onStreakNudgeToggle,
                    onHabitReminders = { navController.navigate(Destination.Reminders.route) },
                    onExport = viewModel::onExport,
                    onLoadDemoData = viewModel::onLoadDemoData,
                    onResetAllData = viewModel::onResetAllData,
                    onBack = { navController.popBackStack() },
                    onResetMessageShown = viewModel::onResetMessageShown,
                    onAiEnabledToggle = viewModel::onAiEnabledToggle,
                    onShowForecastToggle = viewModel::onShowForecastToggle,
                    onDemoAiToggle = viewModel::onDemoAiToggle,
                    onAiHeaderTapped = viewModel::onAiHeaderTapped,
                )
            }
        }
        composable(Destination.Reminders.route) {
            val viewModel = habitlyViewModel { RemindersViewModel(repository) }
            val uiState by viewModel.uiState.collectAsState()
            CenteredContent {
                RemindersScreen(
                    uiState = uiState,
                    onSetReminder = viewModel::onSetReminder,
                    onBack = { navController.popBackStack() },
                )
            }
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
            val weekStart by prefs.weekStart.collectAsState(initial = java.time.DayOfWeek.MONDAY)
            if (uiState.isSaved || uiState.isNotFound) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
            CenteredContent {
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
        }
        composable(
            route = Destination.HabitDetail.route,
            arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val habitId = backStackEntry.arguments?.getLong("habitId") ?: return@composable
            val viewModel = habitlyViewModel { HabitDetailViewModel(repository, habitId, notifications = notifications, weekStart = prefs.weekStart) }
            val uiState by viewModel.uiState.collectAsState()
            if (uiState.isDeleted) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
            if (uiState.justArchived) {
                LaunchedEffect(Unit) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(ARCHIVED_HABIT_KEY, habitId)
                    navController.popBackStack()
                }
            }
            CenteredContent {
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
                    onRestore = viewModel::onRestore,
                    windowWidthSizeClass = windowWidthSizeClass,
                )
            }
        }
    }
}
